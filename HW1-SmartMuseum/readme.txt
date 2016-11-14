
KTH H16P02
Distributed Artificial Intelligence and Intelligent Agents
Homework 1
Due 14.11.16 23:59

Group 25
Fannar Magnússon (fannar@kth.se)
Þorsteinn Þorri Sigurðsson (ttsi@kth.se)

-----------------------

Compile (when in folder where all the .java files are):
javac -d "bin" -cp "path/to/jade.jar" *.java

Run:
java -cp "path/to/jade.jar:bin" jade.Boot -gui -agents "profilerAgent:ProfilerAgent(27,software-engineer,paintings-sculptures-buildings);curatorAgent:CuratorAgent;tourGuideAgent1:TourGuideAgent;tourGuideAgent2:TourGuideAgent"

Example compilation and run:
javac -d "bin" -cp "../../../../../../lib/jade.jar" *.java
java -cp "../../../../../../lib/jade.jar:bin" jade.Boot -gui -agents "profilerAgent:ProfilerAgent(27,software-engineer,paintings-sculptures-buildings);curatorAgent:CuratorAgent;tourGuideAgent1:TourGuideAgent;tourGuideAgent2:TourGuideAgent"