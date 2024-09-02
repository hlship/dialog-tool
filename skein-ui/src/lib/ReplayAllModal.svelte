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
        // abort is set to true when the Modal closes, whether by clicking to close button, or when running is
        // set to false, so we ensure it is set to false before starting the operation.
        abort = false;
        progress = 0;
        label = "";
        running = true;

        let leafs = [];

        for (const [_, knot] of $knots) {
            if (knot.children.length == 0) {
                leafs.push(knot);
            }
        }

        await postApi({ action: "start-batch" });

        let completed = 0;

        for (const leaf of leafs) {
            if (abort) {
                break;
            }

            label = leaf.label || "";

            await postApi({ action: "replay", id: leaf.id });

            progress = 100 * (++completed / leafs.length);
        }

        progress = 100;

        let result = await postApi({ action: "end-batch" });

        dispatcher("result", result);

        running = false;
        abort = false;
    }
</script>

<Modal
    title="Replay All"
    bind:open={running}
    size="sm"
    on:close={() => abort = true}
>
    <div class="h-3">{label}</div>
    <Progressbar {progress} size="h-1.5" color="blue" animate />
</Modal>
