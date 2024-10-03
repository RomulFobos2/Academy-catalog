package com.academy.catalog.service;

import com.academy.catalog.models.VisitorAction;
import com.academy.catalog.repo.VisitorActionRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

@Service
@Getter
@Slf4j
public class VisitorActionService {

    private final VisitorActionRepository visitorActionRepository;

    public VisitorActionService(VisitorActionRepository visitorActionRepository) {
        this.visitorActionRepository = visitorActionRepository;
    }

    @Transactional
    public void saveVisitorAction(String username, String visitorFullName, String documentPath){
        log.info("Сохраняем информацию об открытии документа {} пользователем {} ({})", documentPath, visitorFullName, username);
        VisitorAction visitorAction = new VisitorAction(username, visitorFullName, documentPath);
        try {
            visitorActionRepository.save(visitorAction);
        }
        catch (Exception e){
            log.error("Ошибка при сохранении записи об открытии документа {} пользователем {} ({})", documentPath, visitorFullName, username, e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }
}
