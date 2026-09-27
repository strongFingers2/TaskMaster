package com.example.demo.service;

import com.example.demo.model.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import com.example.demo.repository.TaskRepository;

import java.util.List ;
import java.util.Optional;

@Service
public class TaskService {
    private final TaskRepository taskrepository;
    public TaskService(TaskRepository taskrepository) {
        this.taskrepository = taskrepository;
    }
    public Task createTask(Task task) {
        return taskrepository.save(task);
    }
    public Page<Task> getAllTasks(long ownerId, Pageable pageable) {
        return taskrepository.getTaskByOwnerId(ownerId, pageable);
    }

    public Optional<Task> getTaskById(long id) {
        Optional<Task> t = taskrepository.findById(id);
        return t;
    }

    public Optional<Task> updateTask(long id, Task task) {
        Optional<Task> n = taskrepository.findById(id);
        if (n.isPresent()) {
            Task result = n.get();
            result.setTitle(task.getTitle());
            result.setDescription(task.getDescription());
            taskrepository.save(result);
        }
        return n ;
    }

    public boolean deleteTask(long id) {
        if (taskrepository.existsById(id)) {
            taskrepository.deleteById(id);
            return true;
        }
        return false;
    }
}
