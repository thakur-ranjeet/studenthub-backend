package com.example.demo.service;

import com.example.demo.model.Student;
import com.example.demo.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentServiceImplTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private StudentServiceImpl studentService;

    private Student mockStudent;

    @BeforeEach
    void setUp() {
        mockStudent = new Student();
        mockStudent.setSid(1);
        mockStudent.setSname("Riddhi");
        mockStudent.setSmail("riddhi@gmail.com");
    }

    @Test
    @DisplayName("Should save student and publish event to Kafka successfully")
    void testAddStudent_Success() {
        // Arrange
        when(studentRepository.save(any(Student.class))).thenReturn(mockStudent);
        when(studentRepository.findAll()).thenReturn(Collections.singletonList(mockStudent));

        // Act
        List<Student> result = studentService.addStudent(mockStudent);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Riddhi", result.get(0).getSname());

        // Verify Mock Interactions
        verify(studentRepository, times(1)).save(mockStudent);
        verify(kafkaTemplate, times(1)).send(eq("student-events"), eq(mockStudent));
    }
}