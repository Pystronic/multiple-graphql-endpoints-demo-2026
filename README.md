# multiple-graphql-endpoints-demo

This is a sample demo project that is built to have two separate graphQL endpoints.
One represents an external endpoint with a different/limited schema then the internal endpoint.

GraphiQL is enabled for easier testing, and after starting the app both of the endpoints can be found on:
* http://localhost:8080/graphiql?path=/external-graphql
* http://localhost:8080/graphiql?path=/internal-graphql

## Acknowledgement

The original implement was made by @codensippe and can be found in the following repository:
https://github.com/codesnippe/multiple-graphql-endpoints-demo

## Changes

My version extends the original repository with the following changes:
- Updated dependencies
- Flexible configuration which enables any number of custom endpoints
- Ability to define endpoint specific graphql controllers

## Drawbacks

This approach still needs to bypass the default GraphQlAutoConfiguration, since there is no multi endpoint support in the configuration.
It is also specifically tailored to using HTTP-Requests. Other transport protocols may require additional customization.
