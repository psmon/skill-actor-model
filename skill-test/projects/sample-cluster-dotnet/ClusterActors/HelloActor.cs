using Akka.Actor;

namespace ClusterActors;

public interface IWelcomeMessageProvider
{
    string GetMessage();
}

public sealed class WelcomeMessageProvider : IWelcomeMessageProvider
{
    public string GetMessage() => "wellcome actor world!";
}

public sealed class HelloActor : ReceiveActor
{
    public sealed record Hello(string Name);
    public sealed record HelloResponse(string Message);

    private readonly IWelcomeMessageProvider _welcomeMessageProvider;

    public HelloActor(IWelcomeMessageProvider welcomeMessageProvider)
    {
        _welcomeMessageProvider = welcomeMessageProvider;

        Receive<Hello>(msg =>
        {
            Sender.Tell(new HelloResponse(_welcomeMessageProvider.GetMessage()));
        });
    }
}
