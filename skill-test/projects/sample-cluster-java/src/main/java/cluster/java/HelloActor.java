package cluster.java;

import akka.actor.AbstractActor;
import akka.actor.Props;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component("helloActorBean")
@Scope("prototype")
public class HelloActor extends AbstractActor {

    public static Props props(WelcomeMessageProvider welcomeMessageProvider) {
        return Props.create(HelloActor.class, () -> new HelloActor(welcomeMessageProvider));
    }

    public record Hello(String name) {
    }

    public record HelloResponse(String message) {
    }

    private final WelcomeMessageProvider welcomeMessageProvider;

    public HelloActor(WelcomeMessageProvider welcomeMessageProvider) {
        this.welcomeMessageProvider = welcomeMessageProvider;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(Hello.class, msg -> getSender().tell(new HelloResponse(welcomeMessageProvider.message()), getSelf()))
            .build();
    }
}
