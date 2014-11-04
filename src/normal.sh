#!/bin/bash

SK1=$1
SK2=$2
SK3=$3
SK4=$4
RATIO=$5
INPUT_PATH=$6
OUTPUT_PATH=$7
OUTPUT_NAME=$8

if [ $# -ne 8 ]
then
	echo -e "\t usage: ./normal.sh sk1 sk2 sk3 sk4 ratio inputpath outputpath outputname"
	exit 1
fi

# compile that
rm -f *.class
javac *.java

if [ $? -ne 0 ] #check for compile!!
then
	echo -e "\t + The *.java didn't COMPILE"
	exit # client didnt compile
else
	echo -e "\t + The *.java COMPLIED without ERROR, ADD 10 marks"
fi

#
# kill any thing if running
#

# assuming students have some thing already running
PIDS=$( ps -fu $USER | grep -i "java" | grep -v grep | awk '{print $2}' )

for pid in $PIDS
do
	kill $pid
	echo -e "\t + killed previous thing running with PID "$pid
done

java UnreliNETNormal $SK1 $SK2 $SK3 $SK4 &
#java Receiver $SK2 $SK3 &
#java Sender $SK1 $SK4
java Receiver $SK2 $SK3 $OUTPUT_PATH &
sleep 1
java Sender $SK1 $SK4 $INPUT_PATH $OUTPUT_NAME


#
# kill any thing if running
#

# assuming students have some thing already running
PIDS=$( ps -fu $USER | grep -i "java" | grep -v grep | awk '{print $2}' )

for pid in $PIDS
do
	kill $pid
	echo -e "\t + killed previous thing running with PID "$pid
done










































echo -e "\t + script done"
