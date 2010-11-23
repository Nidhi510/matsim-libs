package playground.mzilske.pipeline;


public class ScenarioOutSwitchTaskManagerFactory extends TaskManagerFactory {

	@Override
	protected TaskManager createTaskManagerImpl(TaskConfiguration taskConfiguration) {
		String numberExpression = taskConfiguration.getDefaultArg();
		return new ScenarioSinkMultiSourceManager(new ScenarioOutSwitchTask(numberExpression));
	}

}
