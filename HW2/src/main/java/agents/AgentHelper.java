package agents;

import jade.lang.acl.ACLMessage;

public class AgentHelper
{
    public static String getAclMessageDisplayString(ACLMessage message)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("Message from ");
        sb.append(message.getSender().getName());
        sb.append(" - [" + ACLMessage.getPerformative(message.getPerformative()) + "] ");
        sb.append("[" + message.getContent() + "]");

        return sb.toString();
    }
}
