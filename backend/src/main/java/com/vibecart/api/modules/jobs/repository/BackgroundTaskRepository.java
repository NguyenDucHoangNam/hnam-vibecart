package com.vibecart.api.modules.jobs.repository;

import com.vibecart.api.modules.jobs.entity.BackgroundTask;
import com.vibecart.api.modules.jobs.entity.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface BackgroundTaskRepository extends JpaRepository<BackgroundTask, String> {

    List<BackgroundTask> findByStatusInAndCreatedAtBefore(List<TaskStatus> statuses, ZonedDateTime cutoff);
}

