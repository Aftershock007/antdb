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

# Evaluating Queries:

```
Flowchart:
+--------------------------+
| Start Query Evaluation   |
+--------------------------+
          |
          v
+--------------------------+
| Parse SQL Statement      |
+--------------------------+
          |
          v
+----------------------------+
| Is Statement Create Table? |
+----------------------------+
          |         |
         Yes       No  
          |         |
          v         v  
+--------------------------+   +----------------------------+
| Throw SQLException:      |   | Is Statement Create Index? |
| Table Creation Not       |   +----------------------------+
| Supported                |              |         |
+--------------------------+             Yes        No  
                                          |         |
                                          v         v  
                                  +--------------------------+
                                  | Throw SQLException:      |
                                  | Index Creation Not       |
                                  | Supported                |
                                  +--------------------------+
                                          |
                                          v  
                                  +--------------------------+
                                  | Is Statement Select?     |
                                  +--------------------------+
                                          |         |
                                         Yes       No  
                                          |         |
                                          v         v  
                                  +--------------------------+
                                  | Get Table from Database  |
                                  +--------------------------+
                                          |
                                          v  
                                  +--------------------------+
                                  | Is Filter Present?       |
                                  +--------------------------+
                                          |         |
                                         Yes       No  
                                          |         |
                                          v         v  
                              +----------------------------+    
                              | Get Rows with Filter       |
                              +----------------------------+
                                          |
                                          v  
                              +----------------------------+    
                              | Retrieve All Rows from     |
                              | Table                      |
                              +----------------------------+
                                          |
                                          v  
                              +----------------------------+    
                              | Evaluate Columns on Rows   |
                              +----------------------------+
                                          |
                                          v  
                              +----------------------------+    
                              | Return Results             |
                              +----------------------------+
                                          |
                                          v  
                                   +---------------+
                                   | End Query     |
                                   | Evaluation    |
                                   +---------------+

```
### Explanation:
1. **Start Query Evaluation**:
    - This is the initial step where the process begins when a user submits an SQL query for evaluation.

2. **Parse SQL Statement**:
    - The SQL statement is parsed using the `Parser` class. This step converts the raw SQL text into an Abstract Syntax Tree (AST), which represents the structure of the query.

3. **Check for Create Table Statement**:
    - The flowchart checks if the parsed statement is a `CREATE TABLE` statement.
    - **Yes**: If it is a `CREATE TABLE`, an exception is thrown indicating that table creation is not supported. This reflects a limitation in your current implementation.
    - **No**: If it is not a `CREATE TABLE`, the flow proceeds to check for a `CREATE INDEX` statement.

4. **Check for Create Index Statement**:
    - Similar to the previous step, this checks if the statement is a `CREATE INDEX`.
    - **Yes**: If it is, an exception is thrown indicating that index creation is not supported.
    - **No**: If it is neither, the flow moves on to check if the statement is a `SELECT` statement.

5. **Check for Select Statement**:
    - The flow checks if the statement is a `SELECT`.
    - **Yes**: If it is a `SELECT`, the engine retrieves the corresponding table from the database based on the table name specified in the query.
    - **No**: If it is not a recognized statement type, an exception can be thrown indicating that it's an unsupported statement.

6. **Check for Filter Presence**:
    - After retrieving the table, the flow checks whether there is a filter condition (e.g., a WHERE clause) present in the SELECT statement.
    - **Yes**: If there is a filter, it calls a method to get rows that match this filter condition.
    - **No**: If no filter exists, it retrieves all rows from the specified table.

7. **Get Rows with Filter**:
    - This step involves retrieving rows from the table based on the specified filter conditions. It may involve using indices if available to optimize retrieval.

8. **Retrieve All Rows from Table**:
    - If no filter was specified, all rows are retrieved from the table directly.

9. **Evaluate Columns on Rows**:
    - Regardless of whether rows were filtered or retrieved in bulk, this step evaluates the specified columns in the SELECT statement against each row retrieved.
    - This involves extracting values based on column names and potentially applying any aggregation functions (like COUNT).

10. **Return Results**:
    - After evaluating all relevant columns and rows, this step returns the results back to the caller.
    - The results may be formatted as a list of rows or another suitable structure based on how data needs to be presented.

11. **End Query Evaluation**:
    - The process concludes after returning results, marking the end of query evaluation.

# Parsing and Executing Queries:
```
+---------------------+
|   Start SQL Query   |
+---------------------+
          |
          v
+---------------------+
|  Initialize Scanner |
+---------------------+
          |
          v
+---------------------+
|   Read Next Token   |
+---------------------+
          |
          v
+---------------------+
|   Is Token EOF?     |
+---------------------+
          |        |
         Yes       No
          |        |
          v        v
+---------------------+      +---------------------+
|  Throw SQLException |<---->|  Parse Expression   |
+---------------------+      +---------------------+
                                   |
                                   v
                          +---------------------+
                          | Is Token a Keyword? |
                          +---------------------+
                                   |        |
                                  Yes       No
                                   |        |
                                   v        v
                          +---------------------+      +---------------------+
                          |   Handle Keyword    |<---->|  Handle Identifier  |
                          +---------------------+      +---------------------+
                                   |
                                   v
                          +---------------------+
                          |  Build AST Node     |
                          +---------------------+
                                   |
                                   v
                          +------------------------+
                          | Is Statement Complete? |
                          +------------------------+
                                   |        |
                                 Yes       No
                                   |        |
                                   v        v
                           +-----------------------+
                           |  Return AST Statement |
                           +-----------------------+
                                   |
                                   v
                           +-----------------------+
                           | Execute AST Statement |
                           +-----------------------+
                                   |
                                   v
                           +-----------------------+
                           |     Retrieve Data     |
                           +-----------------------+
                                   |
                                   v
                           +-----------------------+
                           |   Apply Filters(WHERE)|
                           +-----------------------+
                                   |
                                   v
                           +-----------------------+
                           |   Return Results      |
                           +-----------------------+
                                   |
                                   v
                            +----------------------+
                            |       End            |
                            +----------------------+

```

### Explanation:

1. **Start SQL Query** - The process begins when a user inputs an SQL query.
2. **Initialize Scanner** - A `Scanner` instance is created to tokenize the input string.
3. **Read Next Token** - The scanner reads the next token from the input.
4. **Is Token EOF?** - Check if the end of the input has been reached.
    - If yes, Throw SQLException.
    - If no, go to **Parse Expression**.
5. **Parse Expression** - The parser processes the token to determine its type.
6. **Is Token a Keyword?** - Check if the token is a recognized SQL keyword.
    - If yes, go to **Handle Keyword**.
    - If no, go to **Handle Identifier**.
7. **Handle Keyword/Identifier** - Handle the token accordingly and build an AST node.
8. **Build AST Node** - Construct an appropriate node in the Abstract Syntax Tree (AST).
9. **Is Statement Complete?** - Check if the entire statement has been parsed.
    - If yes, go to **Return AST Statement**.
    - If no, loop back to **Read Next Token**.
10. **Return AST Statement** - Return the constructed AST statement.
11. **Execute AST Statement** - Execute the statement represented by the AST.
12. **Retrieve Data** - Access the relevant data from storage based on the executed statement.
13. **Apply Filters (WHERE)** - Apply any filtering conditions specified in the query.
14. **Return Results** - Return the results of the query execution to the user.
15. **End** - The process concludes after returning results.

# Start to End Process:
```
+--------------------------+
|        Start Process     |
+--------------------------+
          |
          v
+--------------------------+
|   Receive SQL Query      |
| (e.g., SELECT, INSERT)   |
+--------------------------+
          |
          v
+--------------------------+
|       Parse Query        |
| (Syntax & Semantic Check)|
+--------------------------+
          |
          v
+--------------------------+
|  Generate Abstract Syntax|
|         Tree (AST)       |
+--------------------------+
          |
          v
+--------------------------+
|   Optimize Query Plan    |
| (Choose Execution Plan)  |
+--------------------------+
          |
          v
+--------------------------+
|     Execute Query        |
|                          |
|   +------------------+   |
|   |     CRUD Ops     |   |
|   +------------------+   |
|   |                  |   |
|   |  Create Data     |<--|
|   |  (INSERT INTO)   |   |
|   +------------------+   |
|   |                  |   |
|   |  Read Data       |<--|
|   |  (SELECT FROM)   |   |
|   +------------------+   |
|   |                  |   |
|   |  Update Data     |<--|
|   |  (UPDATE SET)    |   |
|   +------------------+   |
|   |                  |   |
|   |  Delete Data     |<--|
|   |  (DELETE FROM)   |   |
|   +------------------+   |
+--------------------------+
          |
          v
+--------------------------+
|      Save Changes        |
|    (Commit Transaction)  |
+--------------------------+
          |
          v
+--------------------------+
|        End Process       |
+--------------------------+
```