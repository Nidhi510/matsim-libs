package playground.mzilske.pipeline;


public class IterationTerminatorTaskManagerFactory extends TaskManagerFactory {

	@Override
	protected TaskManager createTaskManagerImpl(TaskConfiguration taskConfiguration) {
		return new IterationTerminatorTaskManager();
	}

}
