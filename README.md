## Running the app locally for development
### Setup
1. Run `docker-compose up`. This will create a Localstack and initialize it with everything under `./src/main/resources/localstack/`.
2. Wait for localstack to start up and initialize. If it's not ready, the app will crash on boot. 
3. Start the application on CLI or IntelliJ Maven configuration via `mvn clean compile exec:java -Dvertex-configpath="conf/local-config.json"`
4. Once started, admin service runs on `http://localhost:8089/`

### Test data
The data in LocalStack is ephemeral so any changes you make to it will disappear on container restart. If you want
to make permanent changes to test data setup, please change corresponding files under `./src/main/resources/localstack`
and re-initialize your localstack by running `docker-compose restart`.

### Authentication and Authorization
When running locally, GitHub oauth2 is disabled and users are logged in as *test.user@uidapi.com* via the 
`is_auth_disabled` flag. The user has all the rights available. To change the user rights, make changes to 
`src/main/resources/localstack/s3/admins/admins.json` and `docker-compose restart`.