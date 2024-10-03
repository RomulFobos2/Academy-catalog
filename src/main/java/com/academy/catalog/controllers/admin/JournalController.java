package com.academy.catalog.controllers.admin;

import com.academy.catalog.models.User;
import com.academy.catalog.models.VisitorAction;
import com.academy.catalog.service.UserService;
import com.academy.catalog.service.VisitorActionService;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class JournalController {
    private final UserService userService;
    private final VisitorActionService visitorActionService;

    public JournalController(UserService userService, VisitorActionService visitorActionService) {
        this.userService = userService;
        this.visitorActionService = visitorActionService;
    }

    @GetMapping("/admin/journal")
    public String journal(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "searchCategory", required = false) String searchCategory,
            @RequestParam(value = "searchInput", required = false) String searchInput,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Model model) {

        // Если startDate и endDate не переданы, устанавливаем текущую дату и +1 день
        if (startDate == null) {
            startDate = LocalDate.now();
        }
        if (endDate == null) {
            endDate = startDate.plusDays(1);
        }

        // Преобразуем диапазон дат в LocalDateTime
        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to = endDate.atTime(LocalTime.MAX);

        // Пагинация
        Pageable pageable = PageRequest.of(page, 10, Sort.by("timeOfVisit").descending());

        // Проверка выбора категории поиска
        if ("groupByDocument".equals(searchCategory)) {
            // Логика для группировки по имени документа
            List<Object[]> documentCounts = visitorActionService.getVisitorActionRepository()
                    .findDocumentOpenCountsByTimeOfVisitBetween(from, to);

            // Добавляем данные о количестве открытий для отображения на странице
            model.addAttribute("documentCounts", documentCounts);
            model.addAttribute("isGrouped", true);
        } else {
            Page<VisitorAction> visitorActionsPage;

            if ("user".equals(searchCategory)) {
                visitorActionsPage = visitorActionService.getVisitorActionRepository()
                        .findByVisitorFullNameAndTimeOfVisitBetween(searchInput, from, to, pageable);
            } else if ("username".equals(searchCategory)) {
                visitorActionsPage = visitorActionService.getVisitorActionRepository()
                        .findByUsernameAndTimeOfVisitBetween(searchInput, from, to, pageable);
            } else if ("document".equals(searchCategory)) {
                visitorActionsPage = visitorActionService.getVisitorActionRepository()
                        .findByDocumentPathAndTimeOfVisitBetween(searchInput, from, to, pageable);
            } else if ("all".equals(searchCategory)) {
                visitorActionsPage = visitorActionService.getVisitorActionRepository()
                        .searchByInputAndTimeOfVisitBetween(searchInput, from, to, pageable);
            } else {
                visitorActionsPage = visitorActionService.getVisitorActionRepository().findAll(pageable);
            }

            // Добавляем данные в модель
            model.addAttribute("allVisitorActions", visitorActionsPage.getContent());
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", visitorActionsPage.getTotalPages());
            model.addAttribute("isGrouped", false);
        }

        // Добавляем значения полей поиска, чтобы они сохранялись на странице
        model.addAttribute("searchCategory", searchCategory);
        model.addAttribute("searchInput", searchInput);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        return "admin/journal/journal";
    }


    @GetMapping("/admin/journal/download")
    public ResponseEntity<byte[]> downloadJournal(
            @RequestParam(value = "searchCategory", required = false) String searchCategory,
            @RequestParam(value = "searchInput", required = false) String searchInput,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) throws IOException {

        // Устанавливаем текущую дату и диапазон дат
        LocalDate currentDate = LocalDate.now();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        if (startDate == null) {
            startDate = LocalDate.now().minusMonths(1); // За месяц по умолчанию
        }
        if (endDate == null) {
            endDate = currentDate;
        }

        // Преобразуем даты в LocalDateTime
        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to = endDate.atTime(LocalTime.MAX);

        // Создаем документ DOCX
        XWPFDocument document = new XWPFDocument();
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER); // Центрирование текста
        XWPFRun run = paragraph.createRun();

        // Добавляем заголовок и дату формирования журнала
        run.setBold(true);
        run.setFontSize(14);
        run.setText("Журнал посещений");
        run.addBreak();
        run.setText("Дата формирования журнала: " + currentDate.format(dateFormatter));
        run.addBreak();

        // Добавляем условия поиска
        if (searchCategory != null && !searchCategory.isEmpty()) {
            String srtSearchCategory = "";
            switch (searchCategory){
                case "user" : srtSearchCategory = "По имени пользователя"; break;
                case "username" : srtSearchCategory = "По логину"; break;
                case "document" : srtSearchCategory = "По имени документа"; break;
                case "groupByDocument" : srtSearchCategory = "Сгруппировать по имени документа"; break;
                default: srtSearchCategory = "По всем столбцам";
            }
            run.setText("Категория поиска: " + srtSearchCategory);
            run.addBreak();
        }

        if (searchInput != null && !searchInput.isEmpty() && !"groupByDocument".equals(searchCategory)) {
            run.setText("Поисковый запрос: " + searchInput);
            run.addBreak();
        }

        // Форматируем период поиска
        run.setText("Период поиска с: " + startDate.format(dateFormatter) + " по: " + endDate.format(dateFormatter));
        run.addBreak();
        run.addBreak();

        if ("groupByDocument".equals(searchCategory)) {
            // Группировка по имени документа
            List<Object[]> documentCounts = visitorActionService.getVisitorActionRepository()
                    .findDocumentOpenCountsByTimeOfVisitBetween(from, to);

            // Создаем таблицу с группировкой по имени документа
            XWPFTable table = document.createTable();

            // Добавляем заголовки таблицы
            XWPFTableRow tableRowOne = table.getRow(0);
            XWPFTableCell docHeader = tableRowOne.getCell(0);
            docHeader.setText("Имя документа");
            centerText(docHeader);

            XWPFTableCell countHeader = tableRowOne.addNewTableCell();
            countHeader.setText("Количество открытий");
            centerText(countHeader);

            // Заполняем таблицу данными
            for (Object[] result : documentCounts) {
                XWPFTableRow row = table.createRow();
                XWPFTableCell documentCell = row.getCell(0);
                documentCell.setText((String) result[0]);
                centerText(documentCell);

                XWPFTableCell countCell = row.getCell(1);
                countCell.setText(String.valueOf((Long) result[1]));
                centerText(countCell);
            }

        } else {
            // Получаем данные для журнала
            List<VisitorAction> visitorActions = visitorActionService.getVisitorActionRepository()
                    .searchByInputAndTimeOfVisitBetween(searchInput, from, to, Pageable.unpaged()).getContent()
                    .stream().sorted(Comparator.comparing(VisitorAction::getTimeOfVisit).reversed())
                    .collect(Collectors.toList());

            // Создаем таблицу для стандартных данных
            XWPFTable table = document.createTable();

            // Добавляем заголовки таблицы
            XWPFTableRow tableRowOne = table.getRow(0);
            XWPFTableCell indexHeader = tableRowOne.getCell(0);
            indexHeader.setText("Индекс");
            centerText(indexHeader);

            XWPFTableCell userHeader = tableRowOne.addNewTableCell();
            userHeader.setText("Пользователь");
            centerText(userHeader);

            XWPFTableCell usernameHeader = tableRowOne.addNewTableCell();
            usernameHeader.setText("Логин");
            centerText(usernameHeader);

            XWPFTableCell documentHeader = tableRowOne.addNewTableCell();
            documentHeader.setText("Имя документа");
            centerText(documentHeader);

            XWPFTableCell dateHeader = tableRowOne.addNewTableCell();
            dateHeader.setText("Дата открытия");
            centerText(dateHeader);

            // Заполняем таблицу данными
            int index = 1;
            for (VisitorAction action : visitorActions) {
                XWPFTableRow row = table.createRow();

                XWPFTableCell indexCell = row.getCell(0);
                indexCell.setText(String.valueOf(index));
                centerText(indexCell);

                XWPFTableCell userCell = row.getCell(1);
                userCell.setText(action.getVisitorFullName());
                centerText(userCell);

                XWPFTableCell usernameCell = row.getCell(2);
                usernameCell.setText(action.getUsername());
                centerText(usernameCell);

                XWPFTableCell documentCell = row.getCell(3);
                documentCell.setText(action.getDocumentPath());
                centerText(documentCell);

                XWPFTableCell dateCell = row.getCell(4);
                dateCell.setText(action.getTimeOfVisit().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
                centerText(dateCell);

                index++;
            }
        }

        // Записываем документ в массив байтов
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        document.write(outputStream);
        document.close();

        // Устанавливаем заголовки для загрузки файла
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename("journal.docx").build());
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        return new ResponseEntity<>(outputStream.toByteArray(), headers, HttpStatus.OK);
    }




    // Метод для центрирования текста в ячейке
    private void centerText(XWPFTableCell cell) {
        CTTc ctTc = cell.getCTTc();
        CTP ctp = ctTc.getPArray(0);
        CTPPr ctppr = ctp.getPPr() != null ? ctp.getPPr() : ctp.addNewPPr();
        CTJc ctjc = ctppr.isSetJc() ? ctppr.getJc() : ctppr.addNewJc();
        ctjc.setVal(STJc.CENTER); // Горизонтальное выравнивание

        // Устанавливаем вертикальное выравнивание
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
    }


}
