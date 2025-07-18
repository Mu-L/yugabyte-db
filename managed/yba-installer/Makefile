TOP_BUILDDIR ?= .
DEVOPS_HOME=$(CURDIR)/../devops


VERSION ?= $(shell cat $(CURDIR)/version.txt)
BUILD_ID ?= $(shell cat $(CURDIR)/version_metadata.json | jq '.build_id')

# go compile options
GOOS ?= linux
GOARCH ?= amd64

# Build Tags
BUILD_TYPE := preview
USE_LICENSE := true

BUILD_TAGS = ${BUILD_TYPE}
ifeq ($(USE_LICENSE), false)
	BUILD_TAGS := ${BUILD_TAGS},nolicense
endif

# build requirements
BIN_NAME ?= yba-ctl
LICENSE_GEN := licensectl

BIN_DIR := ${TOP_BUILDDIR}/bin
PACKAGE_DIR := ${TOP_BUILDDIR}/yba_installer-${VERSION}-${GOOS}-${GOARCH}
PACKAGE_NAME ?= ${PACKAGE_DIR}.tar.gz

CONFIG_FILE_FOLDER:=$(CURDIR)/config/templates
CONFIG_INPUT_FILE_NAME:=$(CURDIR)/yba-ctl.yml.reference
CONFIG_EMBEDED_NAME:=$(CURDIR)/pkg/config/yba-ctl.yml
VERSION_METADATA_JSON:=$(CURDIR)/version_metadata.json


#pre-reqs
PREREQS_DIR := ${TOP_BUILDDIR}/prereqs

# Prometheus Version
PROMETHEUS_VERSION ?= 3.2.1

# https://github.com/yugabyte/yba-pg-build/releases/tag/2025-03-05-17-09
POSTGRES_VERSION ?= 14.17
PG_BINARY_NAME ?= postgres-linux-${POSTGRES_VERSION}.tar.gz
PG_BINARY_URL := https://s3.us-west-2.amazonaws.com/uploads.dev.yugabyte.com/yba-pg-build/${PG_BINARY_NAME}

# https://adoptium.net/temurin/releases/
JDK_BINARY_NAME ?= OpenJDK17U-jre_x64_linux_hotspot_17.0.7_7.tar.gz
JDK_BINARY_URL ?= https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.7%2B7/OpenJDK17U-jre_x64_linux_hotspot_17.0.7_7.tar.gz
# Package stable ybdb release in yba-installer.
# Currently disabled.
YBDB_BINARY_NAME ?= yugabyte-2.17.2.0-b216-linux-x86_64.tar.gz
YBDB_BINARY_URL ?= https://s3.us-west-2.amazonaws.com/downloads.yugabyte.com/releases/2.17.2.0/yugabyte-2.17.2.0-b216-linux-x86_64.tar.gz



all: prereqs test yba-ctl package

prereqs: postgres jdk

echo:
	@echo $(CURDIR)


postgres: ${PREREQS_DIR}/${PG_BINARY_NAME}
${PREREQS_DIR}/${PG_BINARY_NAME}:
	curl -Lo $@ ${PG_BINARY_URL}
	@echo downloaded $@


jdk: ${PREREQS_DIR}/${JDK_BINARY_NAME}
${PREREQS_DIR}/${JDK_BINARY_NAME}:
	curl -Lo $@ ${JDK_BINARY_URL}
	@echo downloaded $@

ybdb: ${PREREQS_DIR}/${YBDB_BINARY_NAME}
${PREREQS_DIR}/${YBDB_BINARY_NAME}:
	curl -Lo $@ ${YBDB_BINARY_URL}
	@echo downloaded $@

yba-ctl: config test ${BIN_DIR}/${BIN_NAME}
${BIN_DIR}/${BIN_NAME}:
	CGO_ENABLED=0 GOOS=${GOOS} GOARCH=${GOARCH} go build -a -o $@ -tags ${BUILD_TAGS} -ldflags=" \
	-X 'github.com/yugabyte/yugabyte-db/managed/yba-installer/pkg/components/ybactl.Version=${VERSION}' \
	-X 'github.com/yugabyte/yugabyte-db/managed/yba-installer/pkg/components/ybactl.BuildID=${BUILD_ID}' \
	-X 'github.com/yugabyte/yugabyte-db/managed/yba-installer/cmd.PostgresVersion=${POSTGRES_VERSION}' \
	-X 'github.com/yugabyte/yugabyte-db/managed/yba-installer/cmd.PrometheusVersion=${PROMETHEUS_VERSION}'"


package: ${PACKAGE_NAME}
${PACKAGE_NAME}: prereqs yba-ctl
	mkdir $(PACKAGE_DIR)
	cp -RL ${BIN_DIR}/${BIN_NAME} ${PREREQS_DIR}/* ${CONFIG_FILE_FOLDER} ${CONFIG_INPUT_FILE_NAME} ${VERSION_METADATA_JSON} ${VERSION_TXT} ${PACKAGE_DIR}
	tar -czvf ${PACKAGE_NAME} ${PACKAGE_DIR} && rm -R ${PACKAGE_DIR}


licensectl: test ${BIN_DIR}/${LICENSE_GEN}
${BIN_DIR}/${LICENSE_GEN}:
	cd licensectl && \
	  CGO_ENABLED=0 GOOS=${GOOS} GOARCH=${GOARCH} go build -a -o ../$@

.PHONY: clean clean-bin clean-prereqs clean-package
clean: clean-bin clean-prereqs clean-package
clean-bin:
	rm -rf bin/*
clean-prereqs:
	rm -rf prereqs/*
clean-package:
	rm -rf ${PACKAGE_DIR}
	rm -rf ${PACKAGE_NAME}

.PHONY: test
test: config
	go test ./...

.PHONY: config
config:
	cp ${CONFIG_INPUT_FILE_NAME} ${CONFIG_EMBEDED_NAME}

.PHONY: integrationtest
integrationtest: config
	go test -tags integration ./integrationtests/...