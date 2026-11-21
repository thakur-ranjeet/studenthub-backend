package com.example.demo.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import com.example.demo.dto.StudentEventDto;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class NotificationConsumer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "student-events", groupId = "notification-group")
    public void consumeStudentRegistration(String rawJsonMessage) {
        System.out.println("\n🔔 >>>>>>>> NOTIFICATION SERVICE ALERT <<<<<<<< 🔔");
        try {
            // Map JSON string to our local DTO
            StudentEventDto student = objectMapper.readValue(rawJsonMessage, StudentEventDto.class);
            
            System.out.println("Processing notification request for: " + student.getSname());
            System.out.println("Sending confirmation email to: " + student.getSmail());
            System.out.println("📬 SIMULATION: Welcome email successfully dispatched for Student ID: " + student.getSid());
            
        } catch (Exception e) {
            System.out.println("❌ Notification Processing Error!");
            e.printStackTrace();
        }
        System.out.println("🔔 >>>>>>>> ================================== <<<<<<<< 🔔\n");
    }
}
