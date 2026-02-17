package cluster.java;

import org.springframework.stereotype.Component;

@Component
public class WelcomeMessageProvider {
    public String message() {
        return "wellcome actor world!";
    }
}
