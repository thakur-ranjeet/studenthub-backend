package com.example.demo.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.example.demo.model.Student;
import com.example.demo.repository.StudentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class StudentEventConsumer {

    // This object handles converting the raw text string into our Java DTO
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private StudentRepository studentRepository; // we have injected repositoy here..!
    
    @KafkaListener(topics = "student-events", groupId = "course-group")
    public void consumeStudentEvent(String rawJsonMessage) {
        System.out.println("\n>>>>>>>> ======================================== <<<<<<<<");
        System.out.println(">>>>>>>> LIVE KAFKA EVENT DETECTED! <<<<<<<<");
        System.out.println("Raw JSON Payload: " + rawJsonMessage);
        
        try {
            // Convert the raw string into DTO or our StudentEventDto class
            StudentEventDto studentDto = objectMapper.readValue(rawJsonMessage, StudentEventDto.class);
            
            System.out.println(">>>>>>>> SUCCESSFULLY PARSED TO OBJECT! <<<<<<<<");
            System.out.println("Student Name: " + studentDto.getSname());
            System.out.println("Target Course ID: " + studentDto.getCrsId());
            System.out.println("Student Email: " + studentDto.getSmail());
            
            // ADDED THIS ENTIRE DATABASE MAP & SAVE LOGIC
            Student studentEntity = new Student();
            studentEntity.setSid(studentDto.getSid());
            studentEntity.setSname(studentDto.getSname());
            studentEntity.setCrsId(studentDto.getCrsId());
            studentEntity.setSmail(studentDto.getSmail());
            
            // Save directly to the MYSQL database via Spring Data JPA
            Student savedStudent = studentRepository.save(studentEntity);
            System.out.println(">>>>>>>> DATABASE PERSISTENCE SUCCESSFUL! <<<<<<<<");
            System.out.println("Saved Student Record ID: " + savedStudent.getSid() + " for Course: " + savedStudent.getCrsId());
            
        } catch (Exception e) {
            System.out.println("❌ Error processing or saving consumer record.");
            e.printStackTrace();
        }
        System.out.println(">>>>>>>> ======================================== <<<<<<<<\n");
    }
}