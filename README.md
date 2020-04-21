Allure Portal (Allure Report Server)
=================================
![Build / Test / Check](https://github.com/kochetkov-ma/allure-server/workflows/Build%20/%20Test%20/%20Check/badge.svg?branch=master)  

[![jdk11](https://camo.githubusercontent.com/f3886a668d85acf93f6fec0beadcbb40a5446014/68747470733a2f2f696d672e736869656c64732e696f2f62616467652f6a646b2d31312d7265642e737667)](https://www.oracle.com/java/technologies/javase-jdk11-downloads.html)
[![gradle](https://camo.githubusercontent.com/f7b6b0146f2ee4c36d3da9fa18d709301d91f811/68747470733a2f2f696d672e736869656c64732e696f2f62616467652f746f6f6c2d677261646c652d626c75652e737667)](https://gradle.org/)
[![junit](https://camo.githubusercontent.com/d2ba89c41121d7c6223c1ad926380235cf95ef82/68747470733a2f2f696d672e736869656c64732e696f2f62616467652f6a756e69742d706c6174666f726d2d627269676874677265656e2e737667)](https://github.com/junit-team/junit4/blob/master/doc/ReleaseNotes4.13.md)

[![checkstyle](https://img.shields.io/badge/checkstyle-google-blue)](https://github.com/checkstyle/checkstyle)
[![pmd](https://img.shields.io/badge/pmd-passed-green)](https://github.com/pmd/pmd)
[![spotbugs](https://img.shields.io/badge/spotbugs-passed-green)](https://github.com/spotbugs/spotbugs)

## About
Allure server for store / aggregate / manage Allure results and generate / manage Allure Reports.

There is only API in first major version with Swagger(OpenAPI) Description.

Just use Spring Boot Jar from Release Page.
   
Works on WebUI is in progress to next major (2.0.0) version.

## Get Started
### Docker
Running as Docker container look at: [readme](https://hub.docker.com/repository/docker/kochetkovma/allure-server)
### Jar 
Get the latest release [Releases](https://github.com/kochetkov-ma/allure-server/releases)   
Download `allure-server.jar`  
Update your jre(jdk) up to [Java 11](https://www.oracle.com/java/technologies/javase/jdk11-archive-downloads.html)  
Execute command `java -jar allure-server.jar`

Got to `http://localhost:8080` - will redirect to OpenAPI (Swagger UI)

### Upload results
Only allure2 supported  
Make some allure results and create `zip` archive with these results, for example `allure-results.zip` in your root dir
```shell
curl --location --request POST 'http://localhost:8080/api/result' \
--form 'allureResults=@/allure-results.zip;type=application/zip'
```
Response:
```
{
    "fileName": "allure-results.zip",
    "uuid": "1037f8be-68fb-4756-98b6-779637aa4670"
}
```
Save `uuid`  
Don't forget specify form item Content type as `application/zip`.  Server works with `zip` archives only!

### Generate report
For generate new report execute `POST` request with `json` body:
```shell
curl --location --request POST 'http://localhost:8080/api/report' \
--header 'Content-Type: application/json' \
--data-raw '{
  "reportSpec": {
    "path": [
      "master",
      "666"
    ],
    "executorInfo": {
      "buildName": "#666"
    }
  },
  "results": [
    "1037f8be-68fb-4756-98b6-779637aa4670"
  ],
  "deleteResults": false
}'
```
Response:
```
{
    "path": "master/666",
    "url": "http://localhost:8080/allure/reports/master/666/index.html"
}
```
Memorize `url`

### Access to generated reports
After generating you can access the report by`http://localhost:8080/allure/reports/master/666/index.html`

You may get all reports
```shell
curl --location --request GET 'http://localhost:8080/api/report'
```
Or by path as branch name `master`
```shell
curl --location --request GET 'http://localhost:8080/api/report?path=master'
```
You may get all uploaded results:
```shell
curl --location --request GET 'http://localhost:8080/api/result'
```
Clear results or reports:
```shell
curl --location --request DELETE 'http://localhost:8080/api/result'
curl --location --request DELETE 'http://localhost:8080/api/report'
```

### Work on the documentation in progress ...