# in dans-datastation-tools/.dans-datastation-tools.yml
#    validate_dans_bag.service_baseurl: 'http://localhost:20330'
# in dd-validate-dans-bag/etc/config.yml:
#    validation.baseFolder must be some ancestor of the bags to test
# start VM
# in dd-validate-dans-bag run: start-service.sh
# in dans-datastation-tools: ~/git/dans/dd-poc-convert-av-dataset/src/test/resources/integration/validate.sh

output_dir=~/git/dans/dd-poc-convert-av-dataset/target/test/IntegrationTest/converted-bags
input_dir=~/git/dans/dd-poc-convert-av-dataset/src/test/resources/integration/input-bags
for file in $input_dir/*/* $output_dir/*/*/*; do
    echo "$file"
    poetry run dans-bag-validate -t MIGRATION "$file"
    echo ""
    echo ""
done
egrep '(Is-Version-Of|Base-|Created)' $output_dir/*/*/*/bag-info.txt | sort
grep -r streaming $output_dir/*/*/*