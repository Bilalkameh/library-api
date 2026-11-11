package com.library.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "Library API", version = "1.0",
        description = "Books, members, borrowing transactions, overdue fines, and reservation history."))
public class LibraryApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(LibraryApiApplication.class, args);
    }
}
