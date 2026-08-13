package com.lab.labtimesheet;

import org.springframework.boot.SpringApplication;

public class TestLabtimesheetApplication {

    public static void main(String[] args) {
        SpringApplication.from(LabtimesheetApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
