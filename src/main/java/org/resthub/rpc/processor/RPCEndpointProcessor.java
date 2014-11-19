package org.resthub.rpc.processor;

import com.google.common.collect.Sets;
import org.resthub.rpc.annotation.RPCEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.Map.Entry;

@Profile("resthub-amqp-annotation")
@Component
public class RPCEndpointProcessor implements ApplicationContextAware, BeanFactoryPostProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RPCEndpointProcessor.class);
    
    /** Default concurrent consumers count (configurable via rpc.properties) */
    public static final int DEFAULT_DEFAULT_THREADCOUNT = 1;
    
    /** Default concurrent consumers count (configurable via rpc.properties) */
    public static int DEFAULT_THREADCOUNT = DEFAULT_DEFAULT_THREADCOUNT;
    
    /** Key : concurrent consumers */
    public static final String KEY_THREADCOUNT = "threadCount";
    
    /** Key : default concurrent consumers */
    public static final String KEY_DEFAULT_THREADCOUNT = "default." + KEY_THREADCOUNT;
    
    /** properties */
    private ResourceBundle bundle;
    
    private ApplicationContext applicationContext;
    
    private ConfigurableListableBeanFactory factory;
    
    public RPCEndpointProcessor() {
        LOGGER.info("init RPCEndpointProcessor");
        initBundle();
    }
    
    /**
     * Chargement du fichier de propriétés.
     * Si le fichier n'est pas trouvé, simule l'ouverture d'un fichier vide
     */
    private void initBundle() {
        try {
            bundle = ResourceBundle.getBundle("rpc");
            // Chargement du timeout par défaut
            if (bundle.containsKey(KEY_DEFAULT_THREADCOUNT)) {
                DEFAULT_THREADCOUNT = Integer.parseInt(bundle.getString(KEY_DEFAULT_THREADCOUNT));
            }
            LOGGER.info(" - default ThreadCount : {}", DEFAULT_THREADCOUNT);
        } catch (MissingResourceException me) {
            // Fichier de propriété non présent, on charge un properties vide
            LOGGER.info(" - rpc.properties not provided, use default threadCount : {}", DEFAULT_THREADCOUNT);
            try {
                bundle = new PropertyResourceBundle( new StringReader("")  );
            } catch (IOException e1) {
                
            }
        }
    }
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        LOGGER.info("setApplicationContext()");
        this.applicationContext = applicationContext;        
    }
    
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
    	LOGGER.info("postProcessBeanFactory()");
    	factory = beanFactory;
        init();
    }
    
    @PostConstruct
    public void init() {
        LOGGER.info("init");
        initializeRPCEndpoints();
    }
    
    // initialisation

    public void initializeRPCEndpoints() {
        long startTime = System.currentTimeMillis();
        LOGGER.info("start initializeRPCEndpoints()");
        final BeanDefinitionRegistry registry = ((BeanDefinitionRegistry)factory);
        final Set<String> activeProfiles = Sets.newHashSet(applicationContext.getEnvironment().getActiveProfiles());
        
        final Map<String, Object> RPCEndpoints = applicationContext.getBeansWithAnnotation(RPCEndpoint.class);
        for (Entry<String, Object> beanEntry : RPCEndpoints.entrySet()) {
            final String beanNameToEndPoint = beanEntry.getKey();
                        
            final Object beanToEndPoint = beanEntry.getValue();
            final Class<? extends Object> endpointClass = beanToEndPoint.getClass();
            final RPCEndpoint annotation = findAnnotationInHierarchy(endpointClass);
              
            String beanName = beanNameToEndPoint + "RPCEndpoint";
              
            if (applicationContext.containsBean(beanName)) {
                LOGGER.error("ApplicationContext already contains bean with name {}", beanName);
                throw new BeanCreationException(beanName, "ApplicationContext already contains bean with name " + beanName);
            } else {
                int threadCount = DEFAULT_THREADCOUNT;
                if (annotation != null) {
                    // On regarde si l'annotation est désactivée pour certains profils
                    if (! "".equals(annotation.disableForProfile()) ) {
                        // C'est le cas, on verifie que le profil en question n'est pas actif
                        if (activeProfiles.contains( annotation.disableForProfile() ) ) {
                            // le profil est actif, on n'initialise pas le endpoint
                            LOGGER.info("  - skip initializing RPCEndpoint with name {}, as profile {} is active", beanName, annotation.disableForProfile());
                            continue;
                        }
                    }
                    
                    // On regarde si l'annotation n'est activé pour certains profils
                    if (! "".equals(annotation.profile()) ) {
                        // C'est le cas, on verifie que le profil en question est actif
                        if (! activeProfiles.contains( annotation.profile() ) ) {
                            // le profil n'est actif, on n'initialise pas le endpoint
                            LOGGER.info("  - skip initializing RPCEndpoint with name {}, as profile {} is inactive", beanName, annotation.profile());
                            continue;
                        }
                    }
                    
                    threadCount = annotation.threadCount();
                }

                LOGGER.info("  - Initializing RPCEndpoint with name {}", beanName);
                
                ConstructorArgumentValues constructorArgs = new ConstructorArgumentValues();
                constructorArgs.addIndexedArgumentValue(0, new RuntimeBeanReference(beanNameToEndPoint) );
              
                MutablePropertyValues properties = new MutablePropertyValues();
                properties.add("connectionFactory", applicationContext.getBean("connectionFactory"));
                properties.add("concurentConsumers", getThreadCount( endpointClass, threadCount ));
                
                RootBeanDefinition beanDefinition = new RootBeanDefinition();
                beanDefinition.setBeanClass(org.resthub.rpc.RPCEndpoint.class);
                beanDefinition.setConstructorArgumentValues(constructorArgs);
                beanDefinition.setPropertyValues(properties);
                beanDefinition.setLazyInit(false);
                beanDefinition.setAbstract(false);
                beanDefinition.setAutowireCandidate(true);
            
                registry.registerBeanDefinition(beanName, beanDefinition);
                
                // Instanciation de l'objet défini
                applicationContext.getBean(beanName);
                //LOGGER.debug("        - bean {} : {}", beanName, o);
            }
        }
        long endTime = System.currentTimeMillis();
        LOGGER.info("RPCEndpointProcessor Application Context scan + registering completed, took {} ms.", endTime - startTime);
    }

    /**
     * Recherche l'annotation dans la hiérarchie de classe
     * @param endpointClass la classe du endpoint
     * @return l'annotation si trouvée, <code>null</code> sinon
     */
    private RPCEndpoint findAnnotationInHierarchy(Class<? extends Object> endpointClass) {
        RPCEndpoint result = null;
        
        // On parcourt la hiérarchie des classes jusqu'à tomber sur null
        while (endpointClass != null) {
            // On recherche l'annotation sur la classe
            result = endpointClass.getAnnotation(RPCEndpoint.class);
            if (result != null) {
                // On a trouvé l'annotation, on sort de la boucle !
                break;
            } else {
                // On n'a pas trouvé l'annotation, on remonte à la classe parente
                endpointClass = endpointClass.getSuperclass();
            }
        }
        return result;
    }
    
    /**
     * On va rechercher le timeout par défaut dans la classe, et, à défaut, dans les 
     * interfaces impléméntées, et, à défaut, dans les classes parentes 
     * @param beanClass
     * @param annotationThreadCount le nombre de threads défini dans l'annotation
     * @return
     */
    private int getThreadCount(final Class<?> beanClass, final int annotationThreadCount) {
        // Recherche pour la classe en cours
        Integer threadCount = getThreadCount(beanClass.getName() + "." + KEY_THREADCOUNT);
        if (threadCount != null) return threadCount;
        
        // Recherche dans les interfaces
        for (Class<?> implementedInterface : beanClass.getInterfaces()) {
            threadCount = getThreadCount(implementedInterface.getName() + "." + KEY_THREADCOUNT);
            if (threadCount != null) return threadCount;
        }
        
        // Recherche dans les classes parentes
        Class<?> superClass = beanClass.getSuperclass();
        if (superClass != null) {
            return getThreadCount(superClass, annotationThreadCount);
        }
        
        if (annotationThreadCount <= DEFAULT_THREADCOUNT) {
            LOGGER.info("  -  (use default threadCount : {})", DEFAULT_THREADCOUNT);
            return DEFAULT_THREADCOUNT;
        } else {
            LOGGER.info("  -  (use annotation threadCount : {})", annotationThreadCount);
            return annotationThreadCount;
        }
    }

    private Integer getThreadCount(String key) {
        if (bundle.containsKey(key)) {
            Integer threadCount = Integer.parseInt( bundle.getString(key) );
            LOGGER.info("  -  (threadCount : {} (found for key {}))", threadCount, key);
            return threadCount;
        }
        return null;
    }

}
