package com.example.demo.controller;

import com.example.demo.exceptionClasses.TaskNotFound;
import com.example.demo.exceptionClasses.UnauthorizedAccess;
import com.example.demo.model.Task;
import com.example.demo.model.User;
import com.example.demo.service.TaskService;
import com.example.demo.token.JwtUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.demo.service.UserService;
import com.example.demo.DTO.TaskResponseDTO;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class hell {
    private final TaskService taskService;
    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    public hell(TaskService taskService, UserService userService, AuthenticationManager authenticationManager, JwtUtil jwtUtil) {
        this.taskService = taskService;
        this.userService = userService;
        this.authenticationManager = authenticationManager ;
        this.jwtUtil = jwtUtil;
    }
    @PostMapping("/tasks")
    public ResponseEntity<TaskResponseDTO> createTask(@RequestBody Task task, @AuthenticationPrincipal UserDetails userDetails) {

        String username = userDetails.getUsername();
        Optional<User> user = userService.getUserByUsername(username);
        if (!user.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        task.setOwner(user.get());
        Task create = taskService.createTask(task);
        TaskResponseDTO taskResponseDTO = new TaskResponseDTO(create);
        return ResponseEntity.ok(taskResponseDTO);
    }
    @PutMapping("/tasks/{id}")
    public ResponseEntity<TaskResponseDTO> updateTask(@PathVariable long id, @RequestBody Task task, @AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails.getUsername();
        Optional<Task> target = taskService.getTaskById(id);
        if (target.isPresent()) {
            Task targetTask = target.get();
            if (targetTask.getOwner().getId() == userService.getUserByUsername(username).get().getId()) {
                Optional<Task> t = taskService.updateTask(id, task);
                TaskResponseDTO taskResponseDTO = new TaskResponseDTO(t.get());
                return ResponseEntity.ok(taskResponseDTO);
            }
            else {
                throw new UnauthorizedAccess(id, "You cannot update this task.");
            }
        }
        throw new TaskNotFound(id, "Task not found");
    }
    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable long id, @AuthenticationPrincipal UserDetails userDetails) {
        Optional<Task> t = taskService.getTaskById(id);
        String username = userDetails.getUsername();
        if (t.isPresent()) {
            Task task = t.get();
            if (task.getOwner().getId() == userService.getUserByUsername(username).get().getId()) {
                taskService.deleteTask(id);
                return ResponseEntity.noContent().build();
            }
            throw new UnauthorizedAccess(id, "You are not allowed delete this task.");
        }
        throw new TaskNotFound(id, "Task not found");
    }
    public TaskResponseDTO convertTaskResponseDTO(Task task) {
        return new TaskResponseDTO(task);
    }
    @GetMapping("/tasks")
    public List<TaskResponseDTO> getAllTasks(@AuthenticationPrincipal UserDetails userDetails, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        long id = userService.getUserByUsername(userDetails.getUsername()).get().getId();
        Page<Task> allTasks = taskService.getAllTasks(id, pageable);
        List<TaskResponseDTO> allTaskDTOs = allTasks.stream().map(task -> new TaskResponseDTO(task)).collect(Collectors.toList());
        return allTaskDTOs;
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<TaskResponseDTO> getTaskById(@AuthenticationPrincipal UserDetails userDetails, @PathVariable long id) {
        Optional<Task> desire = taskService.getTaskById(id);
        if (desire.isPresent()) {
            String username = userDetails.getUsername();
            if (userService.getUserByUsername(username).get().getId() == desire.get().getOwner().getId()) {

                TaskResponseDTO taskResponseDTO = new TaskResponseDTO(desire.get());
                return ResponseEntity.ok(taskResponseDTO);
            }
            else {
                throw new UnauthorizedAccess(id, "You are not allowed to view this task.");
            }
        }
        throw new TaskNotFound(id, "No such task exists.");
    }

    @PostMapping("/register")
    public ResponseEntity<String> newUser(@RequestBody User user) {
        User n;
        try {
            String username = user.getUsername();
            String password = user.getPassword();
            n = userService.registerUser(password, username);
        }
        catch (IllegalStateException e) {
            return ResponseEntity.status(409).build();
        }
        return ResponseEntity.status(201).body(n.getUsername());
    }
    public record LoginInfo(String username, String password) {}
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody LoginInfo loginInfo) {
        String username = loginInfo.username ;
        String password = loginInfo.password ;
        UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(username, password);
        try {
            authenticationManager.authenticate(usernamePasswordAuthenticationToken);
            String token = jwtUtil.generateToken(username);
            return ResponseEntity.ok(token);
        } catch (Exception e) {
            return ResponseEntity.status(401).build();
        }
    }
}