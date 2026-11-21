package com.example.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.example.demo.model.Student;

@Repository
public interface StudentRepository extends JpaRepository<Student, Integer>{
     //No need to write any method manually!
	//Spring Data JPA automatically gives you .save(), .find(), .findById(), .delete(), etc.
}
