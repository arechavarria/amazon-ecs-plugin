package com.cloudbees.jenkins.plugins.amazonecs;

import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;
import hudson.model.Computer;
import hudson.slaves.Cloud;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

// ECS classes
import com.cloudbees.jenkins.plugins.amazonecs.ECSCloud;
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate;
import com.cloudbees.jenkins.plugins.amazonecs.ECSSlave;
import com.cloudbees.jenkins.plugins.amazonecs.ECSComputer;

@Extension
public class ECSAgentPoolMaintainer extends AsyncPeriodicWork {

    private static final Logger LOGGER = Logger.getLogger(ECSAgentPoolMaintainer.class.getName());

    public ECSAgentPoolMaintainer() {
        super("ECS Agent Pool Maintainer");
    }

    @Override
    public long getRecurrencePeriod() {
        return TimeUnit.MINUTES.toMillis(1);
    }

    @Override
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        for (Cloud c : Jenkins.get().clouds) {
            if (c instanceof ECSCloud) {
                ECSCloud cloud = (ECSCloud) c;
                for (ECSTaskTemplate t : cloud.getAllTemplates()) {
                    if (t.getMinIdleAgents() <= 0 || !t.isScheduleActive()) {
                        continue;
                    }
                    int current = countIdle(t);
                    while (current < t.getMinIdleAgents()) {
                        try {
                            ECSSlave s = cloud.provisionSlaveForTemplate(t);
                            listener.getLogger().println("Pre-launched ECS agent " + s.getNodeName());
                            current++;
                        } catch (Exception ex) {
                            LOGGER.log(Level.WARNING, "Failed to pre-launch agent for template " + t.getTemplateName(), ex);
                            break;
                        }
                    }
                }
            }
        }
    }

    private int countIdle(ECSTaskTemplate template) {
        int count = 0;
        for (Computer c : Jenkins.get().getComputers()) {
            if (c instanceof ECSComputer) {
                ECSSlave node = ((ECSComputer) c).getNode();
                if (node != null && node.getTemplate().equals(template) && c.isIdle()) {
                    count++;
                }
            }
        }
        return count;
    }
}
