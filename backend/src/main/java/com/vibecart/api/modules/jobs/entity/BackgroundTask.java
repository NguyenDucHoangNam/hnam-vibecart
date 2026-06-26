package com.vibecart.api.modules.jobs.entity;

import com.vibecart.api.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "background_tasks")
@Getter
@Setter
public class BackgroundTask extends BaseEntity {

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "task_type", nullable = false, length = 50)
    private String taskType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskStatus status = TaskStatus.PENDING;

    @Column(name = "result_url", columnDefinition = "TEXT")
    private String resultUrl;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public BackgroundTask() {}

    public BackgroundTask(String userId, String taskType, TaskStatus status, String resultUrl, String errorMessage) {
        this.userId = userId;
        this.taskType = taskType;
        this.status = status != null ? status : TaskStatus.PENDING;
        this.resultUrl = resultUrl;
        this.errorMessage = errorMessage;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String userId;
        private String taskType;
        private TaskStatus status = TaskStatus.PENDING;
        private String resultUrl;
        private String errorMessage;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder taskType(String taskType) {
            this.taskType = taskType;
            return this;
        }

        public Builder status(TaskStatus status) {
            this.status = status;
            return this;
        }

        public Builder resultUrl(String resultUrl) {
            this.resultUrl = resultUrl;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public BackgroundTask build() {
            BackgroundTask task = new BackgroundTask(userId, taskType, status, resultUrl, errorMessage);
            if (id != null) {
                task.setId(id);
            }
            return task;
        }
    }
}
