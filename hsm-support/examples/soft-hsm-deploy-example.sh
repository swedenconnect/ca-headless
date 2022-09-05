#!/bin/bash
cd "$(dirname "$0")"

# echo Pulling Docker image ...

# Key identifiers used as alias for the hsm key access
key_id=(
  "ca01-ca" \
	"ca01-ocsp" \
	"root01-ca" \
	"tls-ca" \
	"tls-ocsp")
# Key store locations used as key sources
keystore=(
  "/opt/docker/ca/instances/ca01/keys/key-ca.jks" \
  "/opt/docker/ca/instances/ca01/keys/key-ocsp.jks" \
	"/opt/docker/ca/instances/rot01/keys/rot01-ca.jks" \
	"/opt/docker/ca/instances/tls-client/keys/key-ca.jks" \
	"/opt/docker/ca/instances/tls-client/keys/key-ocsp.jks")
# Passwords for key stores
password=(
	"1234" \
	"1234" \
	"1234" \
	"1234" \
	"1234")
# Aliases for the keystore keys used to export these keys
alias=(
	"ca" \
	"ocsp" \
	"rot-ca" \
	"ca" \
	"ocsp")


echo "extracting keys"
for i in "${!key_id[@]}"; do
	bash softhsm/key-extract.sh -p ${password[$i]} -a ${alias[$i]} -l ${keystore[$i]} -o softhsm/keys/${key_id[$i]}
done

echo "Building docker image with imported keys"
docker build -f softhsm/Dockerfile-key-import \
	--build-arg FROM_IMAGE='headless-ca:m1' \
	--build-arg PIN='s3cr3t' \
	--build-arg SLOT_LABEL='cakeys' \
	--build-arg KEY_DIR='softhsm/keys' \
	--build-arg SCRIPT_DIR='softhsm' \
	-t headless-ca:latest .

echo "Removing key directory"
rm -rf softhsm/keys

echo "Undeploying current running docker image ..."
docker rm hca --force

echo "Re-deploying docker image ..."

docker run -d --name hca --restart=always \
  -p 8080:8080 -p 8009:8009 -p 8443:8443 -p 8000:8000 -p 8006:8006 -p 8008:8008 \
  -e "SPRING_CONFIG_ADDITIONAL_LOCATION=/opt/ca/" \
  -e "SPRING_PROFILES_ACTIVE=nodb, softhsm" \
  -e "TZ=Europe/Stockholm" \
  -v /etc/localtime:/etc/localtime:ro \
  -v /opt/docker/ca/:/opt/ca \
  headless-ca:latest

# Display log (optional)
docker logs hca -f --tail 100