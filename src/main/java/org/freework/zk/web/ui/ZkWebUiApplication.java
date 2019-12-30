package org.freework.zk.web.ui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

@SpringBootApplication
@ServletComponentScan
public class ZkWebUiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZkWebUiApplication.class, args);
    }

}
