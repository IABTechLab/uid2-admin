## Running the app locally for development
### Setup
Run `docker-compose up`. This will create a Localstack and initialize it with everything under `./src/main/resources/localstack/`.
Once started, admin service runs on `http://localhost:8089/`

### Test data
The data in LocalStack is ephemeral so any changes you make to it will disappear on container restart. If you want
to make permanent changes to test data setup, please change corresponding files under `./src/main/resources/localstack`
and re-initialize your localstack by running `docker-compose restart`.

### Authentication and Authorization
When running locally, GitHub oauth2 is disabled and users are logged in as *test.user@uidapi.com* via the 
`is_auth_disabled` flag. The user as all the rights. To change the user rights, make changes to 
`src/main/resources/localstack/s3/admins/admins.json` and `docker-compose restart`.