# Stage 1: Build
FROM mcr.microsoft.com/dotnet/sdk:9.0 AS build
WORKDIR /src
COPY ClusterActors/ClusterActors.csproj ClusterActors/
RUN dotnet restore ClusterActors/ClusterActors.csproj
COPY ClusterActors/ ClusterActors/
RUN dotnet publish ClusterActors/ClusterActors.csproj -c Release -o /app/publish

# Stage 2: Runtime
FROM mcr.microsoft.com/dotnet/runtime:9.0
WORKDIR /app
COPY --from=build /app/publish ./
EXPOSE 4053
ENTRYPOINT ["dotnet", "ClusterActors.dll"]
