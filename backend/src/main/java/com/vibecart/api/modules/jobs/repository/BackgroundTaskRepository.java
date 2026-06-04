package com.vibecart.api.modules.jobs.repository;

import com.vibecart.api.modules.jobs.entity.BackgroundTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BackgroundTaskRepository extends JpaRepository<BackgroundTask, String> {
}
