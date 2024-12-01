<script lang="ts">
    import { postApi, type ActionResult } from "./common.svelte";
    import { Modal, Progressbar } from "flowbite-svelte";
    import type { KnotData } from "./types";

    interface Props {
        knots : Map<number, KnotData>,
        processResult : (result: ActionResult) => void
    }

    let { knots, processResult } : Props = $props();

    let running = $state(false);

    let label = $state(null);
    let progress = $state(0);
    let abort = $state(false);

    export async function run() : Promise<void> {
        // abort is set to true when the Modal closes, whether by clicking to close button, or when running is
        // set to false, so we ensure it is set to false before starting the operation.
        abort = false;
        progress = 0;
        label = "";
        running = true;

        let leafs = new Array<KnotData>();

        for (const [_, knot] of knots) {
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

        processResult(result);

        running = false;
        abort = false;
    }
</script>

<Modal
    title="Replay All"
    bind:open={running}
    size="sm"
    onclose={() => (abort = true)}
>
    <div class="h-3">{label}</div>
    <Progressbar {progress} size="h-1.5" color="blue" animate />
</Modal>
