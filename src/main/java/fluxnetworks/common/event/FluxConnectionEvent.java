package fluxnetworks.common.event;

import fluxnetworks.api.network.IFluxNetwork;
import fluxnetworks.api.tileentity.IFluxConnector;
import net.minecraftforge.fml.common.eventhandler.Event;

public class FluxConnectionEvent extends Event {

    public final IFluxConnector flux;

    public FluxConnectionEvent(IFluxConnector flux) {
        super();
        this.flux = flux;
    }

    public static class Connected extends FluxConnectionEvent {

        public final IFluxNetwork network;

        public Connected(IFluxConnector flux, IFluxNetwork network) {
            super(flux);
            this.network = network;
        }
    }

    public static class Disconnected extends FluxConnectionEvent {

        public final IFluxNetwork network;

        public Disconnected(IFluxConnector flux, IFluxNetwork network) {
            super(flux);
            this.network = network;
        }
    }
}
