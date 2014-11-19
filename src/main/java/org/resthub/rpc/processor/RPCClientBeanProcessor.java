package org.resthub.rpc.processor;

import org.resthub.rpc.annotation.RPCClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;



/**
 * Bean PostProcessor : for each field annotated {@link RPCClient},
 * a bean AMQPProxyFactoryBean is created and set
 */
@Profile("resthub-amqp-annotation")
@Component
public class RPCClientBeanProcessor
    implements ApplicationContextAware, BeanFactoryPostProcessor, BeanPostProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(RPCClientBeanProcessor.class);
    
    /** Default timeout, in milliseconds (configurable via rpc.properties) */
    public static final String DEFAULT_DEFAULT_TIMEOUT = "5000";
    
    /** Default timeout, in milliseconds (configurable via rpc.properties) */
    public static String DEFAULT_TIMEOUT = DEFAULT_DEFAULT_TIMEOUT;
    
    /** Key : timeout */
    public static final String KEY_TIMEOUT = "readTimeout";
    /** Key : default timeout  */
    public static final String KEY_DEFAULT_TIMEOUT = "default." + KEY_TIMEOUT;
    
    protected ApplicationContext applicationContext;
    protected ConfigurableListableBeanFactory factory;
    protected BeanDefinitionRegistry registry;
    
    protected String beanType = "bean";
    
    private ResourceBundle bundle;
    
    public RPCClientBeanProcessor() {
        super();
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
            if (bundle.containsKey(KEY_DEFAULT_TIMEOUT)) {
                DEFAULT_TIMEOUT = bundle.getString(KEY_DEFAULT_TIMEOUT);
            }
            LOGGER.debug(" - default Timeout : {}ms", DEFAULT_TIMEOUT);
        } catch (MissingResourceException me) {
            // Fichier de propriété non présent, on charge un properties vide
            LOGGER.debug(" - rpc.properties not provided, use default Timeout : {}ms", DEFAULT_TIMEOUT);
            try {
                bundle = new PropertyResourceBundle( new StringReader("")  );
            } catch (IOException e1) {
                // Exception jamais déclenchée car StringReader
            }
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        LOGGER.debug("setApplicationContext()");
        this.applicationContext = applicationContext;
    }
    
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        LOGGER.debug("postProcessBeanFactory()");
        factory = beanFactory;
        registry = (BeanDefinitionRegistry)factory;
    }
    
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        handleBean(beanName, bean);
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }
    
    public void handleBean(String beanName, Object bean) {
        Class<?> beanClass = bean.getClass();
        Class<?> superClass = beanClass;
        while (superClass != null) {
            Field[] fields = superClass.getDeclaredFields();
            
            // On parcourt tous les champs
            for (Field field : fields) {
                // On ne travaille que sur les champs qui possèdent l'annotation
                RPCClient annotation = field.getAnnotation(RPCClient.class);
                if (annotation != null) {
                    injectAMQPProxyFactoryBean(beanName, bean, field, annotation, superClass);
                }
            }
            
            // On parcourt également les méthodes
            Method[] methods = superClass.getDeclaredMethods();
            for (Method method : methods) {
                Annotation[][] methodParameterAnnotations = method.getParameterAnnotations();
                // On ne gère que le cas ou la méthode possède 1 et 1 seul argument
                // (sinon comment appeler la méthode ?)
                if (methodParameterAnnotations.length == 1) {
                    Annotation[] annotations = methodParameterAnnotations[0];
                    for (Annotation annotation : annotations) {
                        if (annotation instanceof RPCClient) {
                            RPCClient rpcClientAnnotation = (RPCClient)annotation;
                            callMethodAndInjectAMQPProxyFactoryBean(beanName, bean, method, rpcClientAnnotation, superClass);
                        }
                    }
                }
            }
            
            superClass = superClass.getSuperclass();
        }
    }

    /**
     * Fait appel à la méthode indiquée pour injecter un client RPClient
     */
    public void callMethodAndInjectAMQPProxyFactoryBean(
            String beanName, Object bean, Method method,
            RPCClient annotation, Class<?> superClass) {
        
        // Récupération du type
        Class<?> beanTypeToInject = method.getParameterTypes()[0];
        BeanData beanData = new BeanData(beanTypeToInject);
        
        // bean à injecter
        Object beanToInject = findBeanToInjectInContext(annotation, beanData);
        
        if (beanToInject != null) {
            try {
                LOGGER.info("         -  invoke method {} on bean {} to inject bean {} of type {}", method.getName(), beanName, beanData.beanNameToInject, beanData.beanTypeNameToInject);
                method.invoke(bean, beanToInject);
            } catch (IllegalAccessException e) {
                LOGGER.error("unable to call method", e);
            } catch (IllegalArgumentException e) {
                LOGGER.error("unable to call method", e);
            } catch (InvocationTargetException e) {
                LOGGER.error("unable to call method", e);
            }
        } else {
            LOGGER.error("no bean of type {} found", beanData.beanTypeNameToInject);
        }
    }

    /**
     * Injection du bean de type {@link org.resthub.rpc.AMQPProxyFactoryBean}
     * @param beanName le nom du bean en cours de traitement
     * @param bean le bean en cours de traitement
     * @param field le champ réceptacle, sur bean en cours de traitement
     * @param annotation l'annotation
     */
    public void injectAMQPProxyFactoryBean(String beanName, Object bean, Field field, RPCClient annotation, Class<?> beanClass) {
        BeanData beanData = new BeanData(field.getType());
        
        // bean à injecter
        Object beanToInject = findBeanToInjectInContext(annotation, beanData);
        
        // On injecte le bean
        if (beanToInject != null) {
            
            // Recherche de la méthode setter, si elle existe
            String setterName = "set" 
                                    + field.getName().substring(0, 1).toUpperCase()
                                    + field.getName().substring(1);
            //log.debug("         -  looking for setter method {}", setterName);
            Method setterMethod = null;
            for (Method method : beanClass.getMethods()) {
                if (method.getParameterTypes().length == 1) {
                    if (setterName.equals(method.getName())) {
                        setterMethod = method;
                        break;
                    }
                }
            }
            
            if (setterMethod != null) {
                // mise à jour via setter
                LOGGER.debug("         -  inject {} {} of type {} on bean {} (field {}) using setter {}", beanType, beanData.beanNameToInject, beanData.beanTypeNameToInject, beanName, field.getName(), setterName);
                try {
                    setterMethod.invoke(bean, beanToInject);
                } catch (IllegalAccessException  e) {
                    LOGGER.error("unable to call setter", e);
                } catch (IllegalArgumentException  e) {
                    LOGGER.error("unable to call setter", e);
                } catch (InvocationTargetException e) {
                    LOGGER.error("unable to call setter", e);
                }
            } else {
                // mise à jour via field
                LOGGER.debug("         -  inject {} {} of type {} on bean {} (field {}) using field", beanType, beanData.beanNameToInject, beanData.beanTypeNameToInject, beanName, field.getName());
                try {                    
                    boolean isOriginalAccessible = field.isAccessible();
                    if (! isOriginalAccessible) {
                        field.setAccessible(true);
                    }
                    field.set(bean, beanToInject);
                    if (! isOriginalAccessible) {
                        field.setAccessible(false);
                    }
                } catch (IllegalArgumentException e) {
                    LOGGER.error("error while injecting inject bean {} of type {} on bean {} (field {})", beanData.beanNameToInject, beanData.beanTypeNameToInject, beanName, field.getName());
                    throw new RuntimeException("error while injecting inject bean "+beanData.beanNameToInject+" of type "+ beanData.beanTypeNameToInject + " on bean "+beanName+" (field "+field.getName()+")", e);
                }catch (IllegalAccessException  e) {
                    LOGGER.error("error while injecting inject bean {} of type {} on bean {} (field {})", beanData.beanNameToInject, beanData.beanTypeNameToInject, beanName, field.getName());
                    throw new RuntimeException("error while injecting inject bean "+beanData.beanNameToInject+" of type "+ beanData.beanTypeNameToInject + " on bean "+beanName+" (field "+field.getName()+")", e);
                }
            }
        } else {
            LOGGER.error("error while injecting AMQPProxyFactoryBean on bean={} field={} : proxy is null", beanName, field.getName());
            throw new RuntimeException("error while injecting AMQPProxyFactoryBean on bean="+beanName+" field="+field.getName()+" : proxy is null");
        }
    }
    
    private class BeanData {
        /** Le type du bean */
        public Class<?> beanTypeToInject;
        /** l'objet Bean si trouvé */
        public Object beanToInject;
        /** le nom du bean à injecter */
        public String beanNameToInject;
        /** la chaine qui représente le type de bean à injecter */
        public String beanTypeNameToInject;
        
        /**
         * Constructeur
         * @param typeToInject
         */
        public BeanData(final Class<?> typeToInject) {
            super();
            this.beanTypeToInject = typeToInject;
            this.beanTypeNameToInject = typeToInject.getSimpleName();
        }
        
        /**
         * Est-ce qu'un bean a été trouvé ?
         * @return
         */
        public boolean hasBeanToInject() {
            return beanToInject != null;
        }
    }

    /**
     * Recherche du bean à injecter dans le contexte Spring. S'il est trouvé, la méthode le
     * stocke dans l'objet {@link BeanData} et le retourne
     * @param annotation
     * @param beanData
     * @return
     */
    public Object findBeanToInjectInContext(RPCClient annotation, BeanData beanData) {
        String defaultBeanNameToInject = beanData.beanTypeNameToInject + "RPCClient";
        
        if (! beanData.hasBeanToInject()) {
            try {
                // Nom du client à injecter
                beanData.beanNameToInject = defaultBeanNameToInject;
                //log.debug("         -  check if bean {} of type {} exists", beanNameToInject, beanTypeNameToInject);
                beanData.beanToInject = applicationContext.getBean(beanData.beanNameToInject, beanData.beanTypeToInject);
                //log.debug("         -  bean {} of type {} already exist, skip creation", beanNameToInject, beanTypeNameToInject);
            } catch (NoSuchBeanDefinitionException e) {
                //log.debug("         -  bean {} of type {} does not exist, create it", beanNameToInject, beanTypeNameToInject);
                beanData.beanToInject = createAMQPProxyFactoryBean(beanData.beanNameToInject, beanData.beanTypeToInject);
            }
        }
        return beanData.beanToInject;
    }

    /**
     * 
     * @param beanName
     * @return
     */
    public Object createAMQPProxyFactoryBean(String beanName, Class<?> beanClass) {
        LOGGER.debug("         -  Initializing {} RPClient with name {}", beanType, beanName);
      
        MutablePropertyValues properties = new MutablePropertyValues();
        properties.add("connectionFactory", applicationContext.getBean("connectionFactory"));
        properties.add("serviceInterface", beanClass.getCanonicalName());
        properties.add("readTimeout", getReadTimeOut(beanClass));
        
        RootBeanDefinition beanDefinition = new RootBeanDefinition();
        beanDefinition.setBeanClass(org.resthub.rpc.AMQPProxyFactoryBean.class);
        beanDefinition.setPropertyValues(properties);
        beanDefinition.setLazyInit(false);
        beanDefinition.setAbstract(false);
        beanDefinition.setAutowireCandidate(true);
    
        registry.registerBeanDefinition(beanName, beanDefinition);
        
        // Instanciation de l'objet défini
        Object o = applicationContext.getBean(beanName);
        return o;
    }

    /**
     * On va rechercher le timeout par défaut dans la classe, et, à défaut, dans les 
     * interfaces impléméntées, et, à défaut, dans les classes parentes 
     * @param beanClass
     * @return
     */
    private String getReadTimeOut(final Class<?> beanClass) {
        // Recherche pour la classe en cours
        String timeout = getReadTimeOut(beanClass.getName() + "." + KEY_TIMEOUT);
        if (timeout != null) return timeout;
        
        // Recherche dans les interfaces
        for (Class<?> implementedInterface : beanClass.getInterfaces()) {
            timeout = getReadTimeOut(implementedInterface.getName() + "." + KEY_TIMEOUT);
            if (timeout != null) return timeout;
        }
        
        // Recherche dans les classes parentes
        Class<?> superClass = beanClass.getSuperclass();
        if (superClass != null) {
            return getReadTimeOut(superClass);
        }

        LOGGER.debug("         -  (use default timeout : {}ms)", DEFAULT_TIMEOUT);
        return DEFAULT_TIMEOUT;
    }

    /**
     * Recherche dans le fichier de propriétés la clé donnée
     * @param key le nom de la clé
     * @return <code>null</code> si non trouvée, le timeout trouvé sinon
     */
    private String getReadTimeOut(String key) {
        if (bundle.containsKey(key)) {
            String timeout = bundle.getString(key) ;
            LOGGER.debug("         -  (timeout : {}ms (found for key {}))", timeout, key);
            return timeout;
        }
        return null;
    }

}
