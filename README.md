## Running the app locally for development
### Setup
1. Run `docker-compose up`. This will create a Localstack and initialize it with everything under `./src/main/resources/localstack/`.
2. Wait for localstack to start up and initialize. If it's not ready, the app will crash on boot. 
3. Start the application on CLI or IntelliJ Maven configuration via `mvn clean compile exec:java -Dvertx-config-path=conf/local-config.json -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory -Dlogback.configurationFile=conf/logback.xml`
4. Once started, admin service runs on `http://localhost:8089/`

### Test data
The data in LocalStack is ephemeral so any changes you make to it will disappear on container restart. If you want
to make permanent changes to test data setup, please change corresponding files under `./src/main/resources/localstack`
and re-initialize your localstack by running `docker-compose restart`.

### Authentication and Authorization
When running locally, GitHub OAuth 2.0 is disabled and users are logged in as *test.user@uidapi.com* via the 
`is_auth_disabled` flag. The user has all the rights available. To change the user rights, make changes to 
`src/main/resources/localstack/s3/admins/admins.json` and `docker-compose restart`.

If you want to test with GitHub OAuth 2.0, you will need to create an OAuth application on GitHub with `http://localhost:8089/oauth2-callback` as the callback URL, then generate a client ID/secret. Once generated, set the `is_auth_disabled` flag to `false`, and copy the client ID/secret into `github_client_id` and `github_client_secret`.

## V2 API

The v2 API is based on individual route provider classes. Each class should provide exactly one endpoint and must implement IRouteProvider. It may accept constructor parameters, which will be auto-wired by our DI system. Currently, DI is configured to provide:
- All the IService classes which are provided to the Admin Verticle.
- The Auth middleware (but see IRouteProvider - you probably don't need it).

### IRouteProvider

IRouteProvider requires a `getHandler` method, which should return a valid handler function - see `GetClientSideKeypairsBySite.java`. This method *must* be annotated with the Path, Method, and Roles annotations.

All classes which implement IRouteProvider will automatically be picked up by DI and registered as route handlers. The route handler will automatically be wrapped by the Auth middleware based on the roles specified in the Roles annotation.

## Dependency injection - current state and plans

We are in the process of introducing dependency injection to the code base. Currently, a number of singletons which are constructed explicitly are provided via `ServicesModule` (for `IService` classes) and the `SingletonsModule` (for other singletons - e.g. the Auth middleware).

Over time, it would be nice to expand what is being constructed via DI and reduce our reliance on manually constructing objects. Once we have all of the dependencies for `AdminVerticle` available via DI, we can stop creating the `V2Router` via DI and instead just create the `AdminVerticle` (DI will then create the `V2Router` for us).