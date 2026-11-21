package com.example.demo.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.example.demo.model.Student;
import com.example.demo.repository.StudentRepository;



@Service
public class StudentServiceImpl implements StudentService {

	@Autowired
	StudentRepository er;
	
	// 1. Inject the kafkaTemplate bean
	@Autowired
	private KafkaTemplate<String, Object> kafkaTemplate;
	
	// 2. Define the topic name
	private static final String TOPIC = "student-events";

	@Override
	public List<Student> addStudent(Student e) {
		// TODO Auto-generated method stub
		//Orignal database save logic
		er.save(e);
		
		// New Kafka event logic
		try {
			System.out.println("Publishing student registration event to kafka...");
			kafkaTemplate.send(TOPIC, e);
		}catch (Exception ex) {
			// Logs kafka failures without crashing the entire save operation
			System.err.println("failed to send event to kafka: " + ex.getMessage());
		}
		
		return er.findAll();
	}

	@Override
	public Student  updateStudent(Integer id,Student t) {
		// TODO Auto-generated method stub
		Optional<Student> obj=er.findById(id);
		Student e=obj.get();
		e.setSname(t.getSname());
		e.setCrsId(t.getCrsId());
		e.setSmail(t.getSmail());
		e.setPassword(t.getPassword());
		er.save(e);
		return er.findById(e.getSid()).get();
	}

	@Override
	public List<Student> deleteStudent(Integer id) {
		
		er.deleteById(id);
		return er.findAll();
	}

	@Override
	public Student getStudentById(Integer id) {
		Optional<Student> obj=er.findById(id);
		return obj.get();
	}

	@Override
	public List<Student> getAllStudent() {
		// TODO Auto-generated method stub
		return er.findAll();
	}

	@Override
	public List<Student> getAllStudentOfCrs(Integer cid) {
		// TODO Auto-generated method stub
		
		return er.findByCrsId(cid);
	}
	}

