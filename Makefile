.PHONY: test test-api test-worker

test: test-api test-worker

test-api:
	cd api && mvn -B -q test

test-worker:
	cd worker && mvn -B -q test
