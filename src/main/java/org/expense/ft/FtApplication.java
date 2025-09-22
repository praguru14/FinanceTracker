package org.expense.ft;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FtApplication {

    public static void main(String[] args) {
        SpringApplication.run(FtApplication.class, args);
    }

}
