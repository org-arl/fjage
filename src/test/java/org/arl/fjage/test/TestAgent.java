package org.arl.fjage.test;

import org.arl.fjage.*;

public class TestAgent extends Agent {

    @Override
    public void init() {
        add(OneShotBehavior.create(() -> System.out.println("Hello, world!")));

        add(PoissonBehavior.create(5000, () -> System.out.println("Hello, world every 5s (Poisson)!")));

        add(TickerBehavior.create(5000, () -> System.out.println("Hello, world every 5s!")));

        add(WakerBehavior.create(5000, () -> System.out.println("Hello, world after 5s!")));
    }
}
