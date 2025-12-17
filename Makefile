### SETUP

init:
	$(MAKE) install

install:
	./gradlew --console=colored quarkusBuild


### EXECUTION

run:
	./gradlew --console=colored quarkusDev

test:
	./gradlew --console=colored clean test

# Flag targets as phony, to tell `make` that these are no file targets
.PHONY: init install run test
