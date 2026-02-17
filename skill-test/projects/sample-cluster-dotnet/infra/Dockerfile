# Stage 1: Build
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src
COPY ClusterActors/ClusterActors.csproj ClusterActors/
RUN dotnet restore ClusterActors/ClusterActors.csproj
COPY ClusterActors/ ClusterActors/
RUN dotnet publish ClusterActors/ClusterActors.csproj -c Release -o /app/publish

# Stage 2: Runtime
FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /app
COPY --from=build /app/publish ./
EXPOSE 8080 4053
ENTRYPOINT ["dotnet", "ClusterActors.dll"]
