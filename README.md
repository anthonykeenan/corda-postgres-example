# Postgres  

This CorDapp is an extension of the Corda Kotlin Car Insurance sample app, available [here](https://github.com/corda/samples-kotlin/tree/master/Features/queryableState-carinsurance).

Additionally, it demonstrates how to create a driver test using docker to run tests using a Postgres database. This can be useful to check that your database migrations will work on Postgres as sometimes there are discrepancies between how migrations run on H2 and Postgres.

It also demonstrates how to use deploynodes to create a local network with Postgres as a backing database.

## Car Insurance -- QueryableState

This CorDapp demonstrates how [QueryableState](https://docs.corda.net/docs/corda-os/api-persistence.html) works in Corda. Corda allows developers
to have the ability to expose some or all parts of their states to a custom database
table using an ORM tools. To support this feature the state must implement
`QueryableState`.

In this CorDapp we would use an `Insurance` state and persist its properties in a
custom table in the database.  The `Insurance` state among other fields also
contains an `VehicleDetail` object, which is the asset being insured. We have used
this `VehicleDetail` to demonstrate _One-to-One_ relationship. Similarly we also
have a list of `Claim` objects in the `Insurance` state which represents claims
made against the insurance. We use them to demonstrate _One-to-Many_ relationship.



## Concepts

A spring boot client is provided with the cordapp, which exposes two REST endpoints
(see `Controller` in the clients module) to trigger the flows.
Use the command `./gradlew bootRun` in the project root folder to run the [Spring Boot
Server](https://spring.io/projects/spring-boot#overview).

### Flows

There are two flow in this cordapp:

1. IssueInsurance: It creates the insurance state with the associated vehicle information.

2. InsuranceClaim: It creates the claims against the insurance.


## Usage

## Pre-Requisites

For development environment setup, please refer to: [Setup Guide](https://docs.corda.net/getting-set-up.html).

Additionally, you will need docker installed to spin up a Postgres database, which is used for both the Postgres integration test and deploy nodes below. 

Note: It can also be done with a locally installed Postgres instance, db parameters are stored in `constants.properties`.


### Running the CorDapp using Postgres as a backend

Start a local Postgres instance via docker using:

```
docker run --name corda-pg -e POSTGRES_PASSWORD=pass1234 -p 5432:5432 -d postgres
```

In order to set up the schemas and users, on Windows run:

```
type postgres-deploynodes\src\main\resources\db_setup.sql | docker exec -i corda-pg psql -h localhost -p 5432 -U postgres
```

On mac/linux:

```
cat postgres-deploynodes/src/main/resources/db_setup.sql | docker exec -i corda-pg psql -h localhost -p 5432 -U postgres
```

Then, in order to start the network and run the migration scripts:

```
./gradlew :postgres-deploynodes:deployNodes
```

And start the nodes by running:

```
./postgres-deploynodes/build/nodes/runnodes
```

To kill the docker instance and clean up any resources used:

```
docker kill corda-pg
docker system prune
```

### Postgres integration test

The integration test in `workflows/src/integrationTest/kotlin/net/corda/samples/carinsurance/DriverBasedTest.kt` can be used as
an example of how to run a full Corda integration test using Postgres as a backend. Often when using H2 as a backend for integration
testing, Postgres specfic migration errors aren't discovered until the node is first installed in production.

Common issues with Postgres migration scripts that can cause a 'table not found' error:
* schema names need to be all lower case
* table names need to be all lower case


### Interacting with the nodes

The Postman collection containing API's calls to the REST endpoints can be imported
from the link: https://www.getpostman.com/collections/ddc01c13b8ab4b5e853b.
Use the option Import > Import from Link option in Postman to import the collection.

<p align="center">
<img src="./clients/src/main/resources/static/Postman_screenshot.png" alt="Postman Import Collection" width="400">
</p>


### Connecting to the Database

The JDBC url to connect to the database would be printed in the console in node
startup. Use the url to connect to the database using a suitable client. The
default username is 'postgres' and the password is 'pass1234' (this can be changed in `constants.properties`).

You can download DBeaver to connect to the Postgres instance [here](https://dbeaver.com/download/lite/). 
