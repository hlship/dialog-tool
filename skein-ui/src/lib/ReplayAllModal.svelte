<script>
    import { getContext, createEventDispatcher } from "svelte";
    import { postApi } from "./common.js";
    import { Modal, Progressbar } from "flowbite-svelte";

    const dispatcher = createEventDispatcher();

    const knots = getContext("knots");

    let running = false;

    let label = null;
    let progress = 0;
    let abort = false;

    export async function run() {

        progress = 0;
        label = null;
        running = true;

        // TODO: Undo/Redo should be around the entire operation, not
        // around each invidiual leaf.
        let leafs = [];

        for (const [_, knot] of $knots) {
            if (knot.children.length == 0) {
                leafs.push(knot);
            }
        }

        let completed = 0;

        for (const leaf of leafs) {
            if (abort) { break; }

            label = leaf.label || "";

            let result = await postApi({ action: "replay", id: leaf.id });

            dispatcher("result", result);

            progress = 100 * (++completed / leafs.length);
        }

        running = false;
        abort = false;
    }
</script>

<Modal title="Replay All" bind:open={running}  size="sm" on:close={ () => abort = true }>
    <div class="h-3">{label}</div>
    <Progressbar
        {progress}
        size="h-1.5"
        color="blue"
        animate
    />
</Modal>
