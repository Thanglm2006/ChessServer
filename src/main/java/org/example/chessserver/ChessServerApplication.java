package org.example.chessserver;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.stereotype.Repository;
import javax.sql.DataSource;

@SpringBootApplication
public class ChessServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChessServerApplication.class, args);
    }
    @Bean
    CommandLineRunner testDatabase(DataSource dataSource) {
        return args -> {
            System.out.println("✅ Successfully connected to database: " + dataSource.getConnection().getMetaData().getURL());
        };
    }
    @Bean
    CommandLineRunner checkBeans(ApplicationContext ctx) {
        return args -> {
            String[] controllers = ctx.getBeanNamesForAnnotation(RestController.class);
            System.out.println("🔍 Found controllers: " + controllers.length);
            for (String controller : controllers) {
                System.out.println("✅ Controller loaded: " + controller);
            }
        };
    }
    @Bean
    CommandLineRunner checkRepositories(ApplicationContext ctx) {
        return args -> {
            String[] repos = ctx.getBeanNamesForAnnotation(Repository.class);
            System.out.println("🔍 Found repositories: " + repos.length);
            for (String repo : repos) {
                System.out.println("✅ Repository loaded: " + repo);
            }
            System.out.println("load repository successful!");
        };
    }
}
