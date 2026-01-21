# AWS Lambda Function for LeakScanner

This Lambda function can be used for serverless scanning of repositories.

## Build

```bash
mvn clean package
```

## Deploy

```bash
aws lambda create-function \
  --function-name leakscanner-scan \
  --runtime java17 \
  --role arn:aws:iam::YOUR_ACCOUNT:role/lambda-execution-role \
  --handler com.leakscanner.lambda.ScanHandler \
  --zip-file fileb://target/leakscanner-lambda-1.0.0.jar
```

## Invoke

```bash
aws lambda invoke \
  --function-name leakscanner-scan \
  --payload '{"owner":"octocat","name":"Hello-World","platform":"github"}' \
  response.json
```
