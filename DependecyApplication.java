package com.hackthon.dependecy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

@SpringBootApplication
public class DependecyApplication {


	public static void main(String[] args) throws IOException, InterruptedException {

		SpringApplication.run(DependecyApplication.class, args);
		NpmDependencyController controller = new NpmDependencyController();
	//	String result = controller.executeCommand("npm audit --json");
		//System.out.println("Audit Result: " + result);
	}

}
