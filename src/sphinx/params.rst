Parameters
==========

.. highlight:: groovy

Simple parameters
-----------------

Parameters are generic key-value pairs that can be read/written using `ParameterReq` messages. Agents often use parameters to provide status information, or to allow users to configure the agent. Parameters also have a special syntax in the shell, making it easy for users to interact with the agent.

In order for an agent to support parameters, it needs to:
1. Create an `enum` implementing the `Parameter`_ interface to list all the supported parameters.
2. Add a `ParameterMessageBehavior`_ to handle `ParameterReq`_ messages.

Let us explore this using an example. In order to expose parameters, we must first define an `enum` with the supported paramaters::

    enum MyParams implements org.arl.fjage.param.Parameter {
      x,
      y
    }

The above `enum MyParams` defines two parameters -- `x` and `y`. An agent supporting these parameters defines them as properties with appropriate getters and setters using the JavaBean convention in Java::

    public class MyAgentWithParams extends org.arl.fjage.Agent {

      private int x = 42;           // read-only parameter
      private String y = "hello";   // read-write parameter

      public int getX() {
        return x;
      }

      public String getY() {
        return y;
      }

      public void setY(String s) {
        y = s;
      }

      public void init() {
        // add the behavior to deal with ParameterReq messages
        add(new org.arl.fjage.param.ParameterMessageBehavior(MyParams.class));
      }

    }

Groovy automatically creates the getters and setters for us, and so the implementation in Groovy would look much simpler::

    class MyAgentWithParams extends org.arl.fjage.Agent {

      final int x = 42                // final tells Groovy that this is read-only
      String y = "hello"              // read-write parameter

      void init() {
        // add the behavior to deal with ParameterReq messages
        add new org.arl.fjage.param.ParameterMessageBehavior(MyParams)
      }

    }

That's it!

Let's try out our agent using the shell:

.. code-block:: console

    bash$ ./fjage.sh
    > container.add 'a', new MyAgentWithParams()
    a
    > a
    « A »

    [MyParams]
      x ⤇ 42
      y = hello

    > a.x
    42
    > a.y
    hello
    > a.y = 'hi'
    hi
    > a
    « A »

    [MyParams]
      x ⤇ 42
      y = hi

    > a.x = 7
    Parameter x could not be set

Since the `x` parameter is read-only (denoted by ⤇), we can only *get* its value, and not *set* it. The `y` parameter, on the other hand allows both getting/setting.

It is important to note that although the notation looks similar to reading/writing an attribute of the class, the actual interaction with the agent is via the `ParameterReq`_ and `ParameterRsp`_ messages. Unlike class attributes, this allows access to parameters in remote containers. We can directly sent the `ParameterReq`_ message to explicitly see this interaction:

.. code-block:: console

    > import org.arl.fjage.param.*
    > a << new ParameterReq()
    ParameterRsp[x*:42 y:hi]
    > a << new ParameterReq().get(MyParams.y)
    ParameterRsp[y:hi]
    > a << new ParameterReq().set(MyParams.y, "howdy?")
    ParameterRsp[y:howdy?]

We can also use the `AgentID` `get`/`set` convenience methods to access parameters:

.. code-block:: console

    > a.get(MyParams.x)
    42
    > a.set(MyParams.y, 'hiya!')
    hiya!

.. tip:: Since there are so many ways to access parameters, which one should you use? The attribute notation `a.x` is simple and clear, and is best used in the shell. This notation is not available in Java or statically typed Groovy code, as it uses Groovy's dynamic features. We, therefore, recommend using the `AgentID.get()` and `AgentID.set()` methods instead from Java/Groovy code.

Dynamic parameters
------------------

We saw that setting and setting parameters stored as agent properties is simple. But what if we wanted to generate the values of the parameters dynamically? From the Java example in the previous section, the answer is obvious. You can generate the value of the parameter dynamically in the getter method. The same applies in Groovy agents. We show an example below::

    class MyAgentWithParams extends org.arl.fjage.Agent {

      int x = 42

      int getY() {
        return x + 7
      }

      void init() {
        add new org.arl.fjage.param.ParameterMessageBehavior(MyParams)
      }

    }

This creates a simple read-write parameter `x` and a dynamic read-only parameter `y`, with a value depending on `x`. Let us test it out:

.. code-block:: console

    bash$ ./fjage.sh
    > container.add 'a', new MyAgentWithParams()
    a
    > a
    « A »

    [MyParams]
      x = 42
      y ⤇ 49

    > a.x = 7
    7
    > a.y
    14

Metadata paramters
------------------

fjåge defines a few *standard* meta-parameters that every agent with a `ParameterMessageBehavior`_ supports:

1. `name`: name of the agent
2. `type`: class of the agent
3. `title`: descriptive title for the agent (defaults to name, if not explicitly defined by agent)
4. `description`: description of the agent

To see how these parameters work, let us modify our agent to add a `title` and `description`::

    class MyAgentWithParams extends org.arl.fjage.Agent {

      final static String title = "My agent with parameters"
      final static String description = "This is a sample agent to demonstrate the use of parameters"

      int x = 42
      int y = 7

      void init() {
        add new org.arl.fjage.param.ParameterMessageBehavior(MyParams)
      }

    }

Now, running the agent, we see the title and description when we lookup the agent parameters:

.. code-block:: console

    bash$ ./fjage.sh
    > container.add 'a', new MyAgentWithParams()
    a
    > a
    « My agent with parameters »

    This is a sample agent to demonstrate the use of parameters

    [MyParams]
      x = 42
      y = 7

Indexed parameters
------------------

Sometimes it is useful to have multiple parameters with the same name, but addressed by a numerical index. As an example, let us consider an agent that provides a telephone directory. It supports three indexed parameters: `firstname`, `lastname` and `phone`::

    enum MyParams implements org.arl.fjage.param.Parameter {
      firstname,
      lastname,
      phone
    }

Indexed parameters are defined using getters/setters with indexes. In addition, a `getParameterList` method needs to be overridden to provide a list of parameters for a given index (different indexes may provide different parameters, if desired)::

    class MyAgentWithParams extends org.arl.fjage.Agent {

      String getFirstname(int i) {
        if (i == 1) return "John"
        if (i == 2) return "Alice"
        return null
      }

      String getLastname(int i) {
        if (i == 1) return "Doe"
        if (i == 2) return "Wonderland"
        return null
      }

      String getPhone(int i) {
        if (i == 1) return "+123456789"
        if (i == 2) return "+987654321"
        return null
      }

      void init() {
        add new org.arl.fjage.param.ParameterMessageBehavior() {
          @Override
          List<? extends org.arl.fjage.param.Parameter> getParameterList(int i) {
            return allOf(MyParams)
          }
        }
      }

    }

Now, we can test the indexed parameters:

.. code-block:: console

    bash$ ./fjage.sh
    > container.add 'a', new MyAgentWithParams()
    a
    > a
    « A »

    > a[1]
    « A »

    [MyParams]
      firstname ⤇ John
      lastname ⤇ Doe
      phone ⤇ +123456789

    > a[2]
    « A »

    [MyParams]
      firstname ⤇ Alice
      lastname ⤇ Wonderland
      phone ⤇ +987654321

    > a[2].firstname
    Alice
    > a[1].phone
    +123456789

.. Javadoc links
.. -------------
..
.. _ParameterMessageBehavior: http://org-arl.github.com/fjage/javadoc/index.html?org/arl/fjage/param/ParameterMessageBehavior.html
.. _Parameter: http://org-arl.github.com/fjage/javadoc/index.html?org/arl/fjage/param/Parameter.html
.. _ParameterReq: http://org-arl.github.com/fjage/javadoc/index.html?org/arl/fjage/param/ParameterReq.html
.. _ParameterRsp: http://org-arl.github.com/fjage/javadoc/index.html?org/arl/fjage/param/ParameterRsp.html
