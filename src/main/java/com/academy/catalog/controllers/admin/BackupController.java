package com.academy.catalog.controllers.admin;

import com.academy.catalog.models.Backup;
import com.academy.catalog.service.BackupService;
import com.academy.catalog.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;

@Controller
@Slf4j
public class BackupController {

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @GetMapping("/admin/backups/allBackups")
    public String allBackups(Model model) {
        model.addAttribute("allBackups", backupService.getBackupRepository().findAll());
        return "admin/backups/allBackups";
    }

    @GetMapping("/admin/backups/addBackup")
    public String addBackup(Model model) {
        // Вызов метода createBackup, который вернёт пустую мапу в случае ошибки
        Map<String, String> backupResult = backupService.createBackup();

        if (backupResult.isEmpty()) {
            // Если мапа пуста, произошла ошибка при создании бэкапа
            log.error("Ошибка создания резервной копии. Подробности смотрите в логах.");
            model.addAttribute("backupError", "Ошибка создания резервной копии. Подробности смотрите в логах.");
            model.addAttribute("allBackups", backupService.getBackupRepository().findAll());
            return "admin/backups/allBackups";
        }

        // Если ошибок нет, сохраняем результаты в базе
        backupResult.forEach((fileName, checksum) -> {
            backupService.getBackupRepository().save(new Backup(fileName, checksum));
            System.out.println("Archive: " + fileName + ", Checksum: " + checksum);
        });

        return "redirect:/admin/backups/allBackups";
    }

    // Метод для удаления резервной копии
    @GetMapping("/admin/backups/deleteBackup/{id}")
    public String deleteBackup(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        if (backupService.deleteBackup(id)) {
            redirectAttributes.addFlashAttribute("successMessage", "Резервная копия успешно удалена.");
        } else {
            redirectAttributes.addFlashAttribute("backupError", "Ошибка при удалении резервной копии. Подробности смотрите в логах.");
        }
        return "redirect:/admin/backups/allBackups";
    }

    // Метод для запроса пользователя о создании резервной копии перед восстановлением
    @GetMapping("/admin/backups/restoreFromBackup/{id}")
    public String confirmRestoreFromBackup(@PathVariable("id") Long id, Model model) {
        Optional<Backup> backupOptional = backupService.getBackupRepository().findById(id);
        if (backupOptional.isPresent()) {
            model.addAttribute("backup", backupOptional.get());
            return "admin/backups/confirmRestore"; // Страница с подтверждением и опцией создания резервной копии
        } else {
            model.addAttribute("backupError", "Резервная копия не найдена.");
            return "redirect:/admin/backups/allBackups";
        }
    }

    // Метод для выполнения восстановления данных
    @PostMapping("/admin/backups/restoreFromBackup/{id}")
    public String restoreFromBackup(@PathVariable("id") Long id,
                                    @RequestParam(value = "createBackup", required = false) boolean createBackup,
                                    RedirectAttributes redirectAttributes) {
        Optional<Backup> backupOptional = backupService.getBackupRepository().findById(id);

        if (backupOptional.isEmpty()) {
            redirectAttributes.addFlashAttribute("backupError", "Резервная копия не найдена.");
            return "redirect:/admin/backups/allBackups";
        }

        Backup backup = backupOptional.get();
        Path archivePath = Paths.get(backupService.getUploadBackupPath(), backup.getArchiveName());

        try {
            // Проверяем контрольную сумму
            String calculatedChecksum = FileService.calculateFileChecksum(archivePath);
            if (!calculatedChecksum.equals(backup.getControlSumma())) {
                redirectAttributes.addFlashAttribute("backupError", "Контрольные суммы не совпадают. Восстановление невозможно.");
                return "redirect:/admin/backups/allBackups";
            }

            // Спрашиваем, нужно ли создать резервную копию текущего состояния
            if (createBackup) {
                backupService.createBackup(); // Создаем резервную копию текущих данных
                log.info("Создана резервная копия текущего состояния перед восстановлением.");
            }

            // Восстановление данных из архива
            boolean success = backupService.restoreFromBackup(archivePath);
            if (!success) {
                redirectAttributes.addFlashAttribute("backupError", "Ошибка при восстановлении данных из резервной копии.");
                return "redirect:/admin/backups/allBackups";
            }

            redirectAttributes.addFlashAttribute("successMessage", "Данные успешно восстановлены из резервной копии.");
            return "redirect:/admin/backups/allBackups";

        } catch (IOException | NoSuchAlgorithmException e) {
            log.error("Ошибка при восстановлении данных из резервной копии '{}': {}", backup.getArchiveName(), e.getMessage());
            redirectAttributes.addFlashAttribute("backupError", "Ошибка при восстановлении данных. Подробности смотрите в логах.");
            return "redirect:/admin/backups/allBackups";
        }
    }
}