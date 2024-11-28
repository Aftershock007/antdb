[//]: # (Prerequisites:)

[//]: # ()
[//]: # (- sdkman &#40;e.g. `curl -s "https://get.sdkman.io" | bash`&#41;)

[//]: # (- jdk &#40;e.g. `sdk install java`&#41;)

[//]: # (- maven &#40;e.g. `sdk install maven`&#41;)

[//]: # ()
[//]: # (Download sample databases:)

[//]: # ()
[//]: # (```)

[//]: # (curl -Lo superheroes.db https://raw.githubusercontent.com/codecrafters-io/sample-sqlite-databases/master/superheroes.db)

[//]: # ()
[//]: # (curl -Lo companies.db https://raw.githubusercontent.com/codecrafters-io/sample-sqlite-databases/master/companies.db)

[//]: # (```)

[//]: # ()
[//]: # (To run tests:)

[//]: # ()
[//]: # (```)

[//]: # (mvn test)

[//]: # (```)

[//]: # (Some example commands:)

[//]: # (```bash)

[//]: # (   ./antdb.sh sample.db .dbinfo)

[//]: # (   ./antdb.sh sample.db "SELECT count&#40;*&#41; FROM oranges")

[//]: # (   ./antdb.sh sample.db "SELECT name FROM apples")

[//]: # (   ./antdb.sh superheroes.db "select name, first_appearance from superheroes where hair_color = 'Brown Hair'")

[//]: # (   ./antdb.sh superheroes.db "select count&#40;*&#41; from superheroes where eye_color = 'Blue Eyes'")

[//]: # (   ./antdb.sh companies.db "SELECT id, name FROM companies WHERE country = 'republic of the congo'")

[//]: # (```)

Install docker image:

[//]: # (```)

[//]: # (docker build -t antdb . )

[//]: # (```)

```
docker pull public.ecr.aws/q2l1y6g6/antdb:latest
```

Example database commands:

[//]: # (```bash)

[//]: # (  docker run --rm -v $&#40;pwd&#41;:/data antdb /data/sample.db .dbinfo)

[//]: # (  docker run --rm -v $&#40;pwd&#41;:/data antdb /data/sample.db "SELECT count&#40;*&#41; FROM oranges")

[//]: # (  docker run --rm -v $&#40;pwd&#41;:/data antdb /data/sample.db "SELECT name FROM apples")

[//]: # (  docker run --rm -v $&#40;pwd&#41;:/data antdb /data/superheroes.db "SELECT name, first_appearance FROM superheroes WHERE hair_color = 'Brown Hair'")

[//]: # (  docker run --rm -v $&#40;pwd&#41;:/data antdb /data/superheroes.db "SELECT count&#40;*&#41; FROM superheroes WHERE eye_color = 'Blue Eyes'")

[//]: # (  docker run --rm -v "$&#40;pwd&#41;:/data" antdb /data/companies.db "SELECT id, name, country FROM companies WHERE country = 'nepal'")

[//]: # (```)

```bash
  docker run --rm -v "$(pwd):/data" public.ecr.aws/q2l1y6g6/antdb /data/sample.db .dbinfo
```
```bash
  docker run --rm -v "$(pwd):/data" public.ecr.aws/q2l1y6g6/antdb /data/sample.db "SELECT count(*) FROM oranges"
```
```bash
  docker run --rm -v "$(pwd):/data" public.ecr.aws/q2l1y6g6/antdb /data/sample.db "SELECT name FROM apples"
```
```bash
  docker run --rm -v "$(pwd):/data" public.ecr.aws/q2l1y6g6/antdb /data/superheroes.db "SELECT name, first_appearance FROM superheroes WHERE hair_color = 'Brown Hair'"
```
```bash
  docker run --rm -v "$(pwd):/data" public.ecr.aws/q2l1y6g6/antdb /data/superheroes.db "SELECT count(*) FROM superheroes WHERE eye_color = 'Blue Eyes'"
```
```bash
  docker run --rm -v "$(pwd):/data" public.ecr.aws/q2l1y6g6/antdb /data/companies.db "SELECT id, name, country FROM companies WHERE country = 'nepal'"
```