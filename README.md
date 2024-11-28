Prerequisites:

- sdkman (e.g. `curl -s "https://get.sdkman.io" | bash`)
- jdk (e.g. `sdk install java`)
- maven (e.g. `sdk install maven`)

Download sample databases:

```
curl -Lo superheroes.db https://raw.githubusercontent.com/codecrafters-io/sample-sqlite-databases/master/superheroes.db

curl -Lo companies.db https://raw.githubusercontent.com/codecrafters-io/sample-sqlite-databases/master/companies.db
```

To run tests:

```
mvn test
```

Some example commands:

```bash
   ./antdb.sh sample.db .dbinfo
   ./antdb.sh sample.db "SELECT count(*) FROM oranges"
   ./antdb.sh sample.db "SELECT name FROM apples"
   ./antdb.sh superheroes.db "select name, first_appearance from superheroes where hair_color = 'Brown Hair'"
   ./antdb.sh superheroes.db "select count(*) from superheroes where eye_color = 'Blue Eyes'"
   ./antdb.sh companies.db "SELECT id, name FROM companies WHERE country = 'republic of the congo'"
```
