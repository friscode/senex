package senex.core;

import static senex.core.Senex.*;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import senex.commands.SenexCommandBase;

@Component
public class SenexCommandDispatcher implements ApplicationRunner {
	@Autowired
	protected Environment env;

	@Autowired
	protected ApplicationContext appContext;

	private static final Logger logger = LoggerFactory.getLogger(SenexCommandDispatcher.class);

	@Override
	public void run(ApplicationArguments args) throws Exception {
		List<String> commands= getCommandsList(args);

		if (commands.isEmpty()) {
			logError(logger, "CommandDispatchFailed", 
					"providedArgs", StringUtils.join(args.getSourceArgs(), ", "),
					"reason", "Atleast one command needs to be specified. ");
			commands.add("about");
		}

		Map<String, String> commandMap = enlistAllAvailableCommands();

		for(String commandName : commands){
			if (commandMap.containsKey(commandName.toLowerCase())) {
				Senex.logInfo(logger, "ResolvingCommandBean", "beanName", commandMap.get(commandName));
				Object o = appContext.getBean(commandMap.get(commandName));
				Senex.out.println("Found object of :"+o);
				if (o != null && o instanceof SenexCommandBase) {
					((SenexCommandBase) o).init();
					((SenexCommandBase) o).execute();
					((SenexCommandBase) o).cleanUp();
				}
			}
		}
	}

	private List<String> getCommandsList(ApplicationArguments args) {
		List<String> commandsList = Lists.newArrayList();
		for(String cmd: args.getSourceArgs()){
			Senex.out.println(cmd);

			if(!cmd.trim().startsWith("--")){
				commandsList.add(cmd);
			}
		}
		return commandsList;
	}
	
	private Map<String, String> enlistAllAvailableCommands(){
		/*
		 * Enlist all command in senex.commands package. These are the classes
		 * which have been annotated as "SenexCommand" Find their bean name and
		 * initialize a map. Use this map for validating invoked command.
		 */
		Map<String, String> commandMap = Maps.newHashMap();
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(SenexCommand.class));
		
		for (BeanDefinition bd : scanner.findCandidateComponents("senex")){
			try{
				Class commandClass = Class.forName(bd.getBeanClassName());
		
				if(AnnotationUtils.isAnnotationDeclaredLocally(SenexCommand.class, commandClass)){
					Senex.out.println("Found a senex command: "+commandClass.getSimpleName());
					
					if (AnnotationUtils.isAnnotationDeclaredLocally(Component.class, commandClass)) {
		
						String commandName = null, commandBeanName = null;
		
						Annotation an = commandClass.getAnnotation(SenexCommand.class);
						if (an != null) {
							commandName = ((SenexCommand) an).name();
						}

						an = commandClass.getAnnotation(Component.class);
						commandBeanName = ((Component)an).value().trim();  
						if( StringUtils.isBlank(commandBeanName)){
							commandBeanName = commandClass.getSimpleName();
							commandBeanName = commandBeanName.substring(0, 1).toLowerCase() + commandBeanName.substring(1);
						}
		
						Senex.out.println("Resoved as : "+ commandName + " - "+ commandBeanName);
						
						if (StringUtils.isNotBlank(commandName) && StringUtils.isNotBlank(commandBeanName)) {
							commandMap.put(commandName.toLowerCase(), commandBeanName);
							logInfo(logger, "EnlistingCommands", 
									"className", bd.getBeanClassName(), 
									"command", commandName.toLowerCase(),
									"commandBeanName", commandBeanName);
						}
						
					}
				}

			}catch(Exception er){
				er.printStackTrace();
			}
		}

		Senex.out.println("Found commands: "+commandMap);
		return commandMap;
	}
}
