Directory Services
==================

.. highlight:: groovy

Advertising services
--------------------

It is often undesirable to hardcode names of agents that we need to interact with. Directory services provide a simple mechanism to advertise services provided by an agent, and to find agents that provide specific services. A *service* is a contract between two agents, and usually defined by a set of messages and possibly behaviors.

.. tip:: A *service* is a logical concept, and not enforced by the framework. The messages and behaviors represented by a service are not described in the code. However, it is recommended that the documentation associated with the service clearly spell out the messages and behaviors that are expected of any agent claiming to provide the service.

An agent providing a service usually advertises the service during `init()`::

    class MyServer extends org.arl.fjage.Agent {
      void init() {
        register 'WeatherForecastService'
      }
    }

Rather than use a `String`, we may (and usually should) use an Enum to define the service::

    enum Services {
      WEATHER_FORECAST_SERVICE,
      CLEANING_SERVICE,
      FOOD_DELIVERY_SERVICE
    }

and then use it to advertise our services::

    class MyServer extends org.arl.fjage.Agent {
      void init() {
        register Services.WEATHER_FORECAST_SERVICE
      }
    }

Looking up service providers
----------------------------

A client interested in availing a specific service can look for an agent that provides the service::

    def weatherStation = agentForService Services.WEATHER_FORECAST_SERVICE
    def rsp = weatherStation << new WeatherForecastReq(city: 'London', country: 'UK')

If there are more than one agents providing the service, the `agentForService()` method returns any one of the service providers. If we wish to get a list of all service providers, we can use the `agentsForService()` method instead::

    def providerList = agentsForService Services.WEATHER_FORECAST_SERVICE

Caching service providers
-------------------------

If your application uses a set of agents that are instantiated in the `initrc.groovy` and not terminated until the application terminates, it may be reasonable to lookup the service providers once and cache them once and for all. However, since the services are only advertised during agent initialization and the order of agent intialization may be indeterminate, the service lookups should be done after all agents are initialized (and not during `init()`). This can easily be accomplished using a one-shot behavior::

    class MyClient extends org.arl.fjage.Agent {
      def weatherStation
      void init() {
        add oneShotBehavior {
          weatherStation = agentForService Services.WEATHER_FORECAST_SERVICE
        }
      }
    }

.. note:: As long as the agents are added to the container before starting the platform, fjåge guarantees that all agents are initialized before any agent behaviors are called.
