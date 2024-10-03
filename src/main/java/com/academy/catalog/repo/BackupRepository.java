package com.academy.catalog.repo;

import com.academy.catalog.models.Backup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackupRepository extends JpaRepository<Backup, Long> {
}
