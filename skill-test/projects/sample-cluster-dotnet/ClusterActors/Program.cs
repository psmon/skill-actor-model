using ClusterActors;
using Akka.Actor;
using Serilog;

var builder = WebApplication.CreateBuilder(args);

builder.Host.UseSerilog((context, _, configuration) =>
{
    configuration
        .ReadFrom.Configuration(context.Configuration)
        .Enrich.FromLogContext()
        .WriteTo.Console()
        .WriteTo.File(
            path: "logs/clusteractors-.log",
            rollingInterval: RollingInterval.Day,
            retainedFileCountLimit: 14,
            shared: true);
});

builder.Services.AddSingleton<IWelcomeMessageProvider, WelcomeMessageProvider>();
builder.Services.AddSingleton<ActorRuntime>();
builder.Services.AddHostedService(sp => sp.GetRequiredService<ActorRuntime>());

builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

var app = builder.Build();

app.UseSwagger();
app.UseSwaggerUI();

app.MapGet("/api/heath", () =>
    Results.Ok(new { status = "UP", service = "sample-cluster-dotnet", timestamp = DateTimeOffset.UtcNow }));

app.MapGet("/api/actor/hello", async (ActorRuntime runtime, CancellationToken ct) =>
{
    var response = await runtime.HelloActor.Ask<HelloActor.HelloResponse>(
        new HelloActor.Hello("hello"),
        TimeSpan.FromSeconds(5),
        cancellationToken: ct);

    return Results.Ok(response);
});

app.MapGet("/api/cluster/info", async (ActorRuntime runtime, CancellationToken ct) =>
{
    var response = await runtime.ClusterInfoActor.Ask<ClusterInfoActor.ClusterInfoResponse>(
        new ClusterInfoActor.GetClusterInfo(),
        TimeSpan.FromSeconds(5),
        cancellationToken: ct);

    return Results.Ok(response);
});

app.MapPost("/api/kafka/fire-event", async (ActorRuntime runtime, CancellationToken ct) =>
{
    var response = await runtime.KafkaStreamSingletonProxy.Ask<KafkaStreamSingletonActor.FireEventResult>(
        new KafkaStreamSingletonActor.FireEvent(),
        TimeSpan.FromSeconds(35),
        cancellationToken: ct);

    return response.Success
        ? Results.Ok(response)
        : Results.Problem(response.Error ?? "Kafka fire-event failed", statusCode: 500);
});

app.Run();
