def generate():
    # Preamble Script.
    # Test Preamble Script To Be Added In The Future.
    set_safety_mode_transition_hardness(1)
    # Configuration Preamble Script.
    # Configuration variables.
    #
    global cleaner_gun_count_value
    cleaner_gun_count_value = 56
    set_tool_analog_io_work_mode(3)
    set_tool_digital_io_work_mode(0)
    set_tool_analog_input_domain(0)
    set_tool_analog_output_domain(0)
    set_tool_digital_io_config(0, 0, False, False)
    set_tool_digital_io_config(1, 0, False, False)
    set_tool_digital_io_config(2, 0, False, False)
    set_tool_digital_io_config(3, 0, False, False)
    tool_serial_config(False, 115200, 0, 1, modbus_rtu=False, usage=0)
    set_tool_voltage(0)
    set_tcp([0.0, 0.0, 0.15, -0.5235987755982988, 0.0, 0.0])
    set_payload(0.0, [0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0])
    set_standard_analog_input_domain(0, 1)
    set_standard_analog_input_domain(1, 1)
    set_standard_analog_output_domain(0, 1)
    set_standard_analog_output_domain(1, 1)
    set_input_actions_to_default()
    set_gravity([0.0, 0.0, 9.82])
    # Frame variables.
    # Temporary frame variables.
    # Conveyor Config
    # --------------- Start WeldingDevice -----------------
    global full_touch_sense_offset_value
    full_touch_sense_offset_value = [0, 0, 0, 0, 0, 0]
    global full_touch_reference_point
    full_touch_reference_point = [0, 0, 0, 0, 0, 0]
    global full_touch_sense_offset_calculate
    full_touch_sense_offset_calculate = [0, 0, 0, 0, 0, 0]
    global full_touch_offset_enable
    full_touch_offset_enable = False
    global in_welding_path
    in_welding_path = False
    global full_sim_mode
    full_sim_mode = True
    global full_sim_mode_inspection_thread_id
    full_sim_mode_inspection_thread_id = 0
    global full_process
    full_process = 0
    global full_firstArc
    full_firstArc = 0
    global full_record_current
    full_record_current = 0
    global full_record_voltage
    full_record_voltage = 0

    def full_repeatable(i):
        global full_firstArc
        if full_firstArc >> i & 1 == 1:
            halt()
        else:
            full_firstArc |= (1 << i)

    end

    def full_setRepeatable(i):
        global full_firstArc
        full_firstArc |= (1 << i)

    end

    def calculate_offset(offset_value, reference_point, deviation_allowance):
        global full_touch_sense_offset_calculate
        rad_to_deg = 180.0 / 3.1415926
        for i in range(len(offset_value)):
            full_touch_sense_offset_calculate[i] = offset_value[i] - reference_point[i]
            if i >= 3:
                full_touch_sense_offset_calculate[i] = full_touch_sense_offset_calculate[i] * rad_to_deg
        for i in range(3):
            if abs(full_touch_sense_offset_calculate[i]) > (deviation_allowance / 1000):
                popup(s="寻位结果超出偏差允许范围，是否要继续运行任务，<是>请点击<继续>，<否>请点击<停止任务>！",
                      error=True, title="接触寻位", blocking=True)

    end

    def one_point_touch_offset(org, touch):
        pose = elicopy.deepcopy(org)
        pose[0] = touch[0]
        pose[1] = touch[1]
        pose[2] = touch[2]
        pose[3] = org[3]
        pose[4] = org[4]
        pose[5] = org[5]
        return pose

    end

    def touch_sense_offset(org, dir1, dir2, length):
        pose = elicopy.deepcopy(org)
        pose_diff = [0, 0, 0]
        pose_diff[0] = dir2[0] - dir1[0]
        pose_diff[1] = dir2[1] - dir1[1]
        pose_diff[2] = dir2[2] - dir1[2]
        pose_diff = normalize(pose_diff)
        pose[0] = pose[0] + pose_diff[0] * length
        pose[1] = pose[1] + pose_diff[1] * length
        pose[2] = pose[2] + pose_diff[2] * length
        return pose

    end

    def full_apply_touch_offset(target_pose):
        if full_touch_offset_enable:
            return pose_trans(full_touch_sense_offset_value, pose_trans_local(target_pose, full_touch_reference_point))
        end
        return target_pose

    end

    def calculation_offset(org, touch):
        offset = [0, 0, 0]
        offset[0] = touch[0] - org[0]
        offset[1] = touch[1] - org[1]
        offset[2] = touch[2] - org[2]
        return offset

    end

    def overlay_first_offset(org, offset):
        pnt = elicopy.deepcopy(org)
        pnt[0] = org[0] + offset[0]
        pnt[1] = org[1] + offset[1]
        pnt[2] = org[2] + offset[2]
        return pnt

    end

    def fail_touch_sense(message):
        popup(s=message, error=True, title="接触寻位", blocking=True)
        halt()

    end

    def is_zero_pose(pnt):
        return pnt == [0] * len(pnt)

    end

    def point_distance(pnt1, pnt2):
        return norm(vector_sub(pnt1, pnt2))

    end

    def average_offset(offset1, offset2):
        offset = [0, 0, 0]
        offset[0] = (offset1[0] + offset2[0]) / 2.0
        offset[1] = (offset1[1] + offset2[1]) / 2.0
        offset[2] = (offset1[2] + offset2[2]) / 2.0
        return offset

    end

    def validate_point_group(points, index_array, min_distance, error_message):
        for i in range(len(index_array)):
            if is_zero_pose(points[index_array[i]]):
                fail_touch_sense(error_message)
            end
            for j in range(i + 1, len(index_array)):
                if point_distance(points[index_array[i]], points[index_array[j]]) < min_distance:
                    fail_touch_sense(error_message)
                end
            end
        end

    end

    def validate_seek_group(seek_org, seek_dir, index_array, min_origin_spacing, min_direction_length, error_message):
        for i in range(len(index_array)):
            if is_zero_pose(seek_org[index_array[i]]):
                fail_touch_sense(error_message)
            end
            if is_zero_pose(seek_dir[index_array[i]]):
                fail_touch_sense(error_message)
            end
            if point_distance(seek_org[index_array[i]], seek_dir[index_array[i]]) < min_direction_length:
                fail_touch_sense(error_message)
            end
            for j in range(i + 1, len(index_array)):
                if point_distance(seek_org[index_array[i]], seek_org[index_array[j]]) < min_origin_spacing:
                    fail_touch_sense(error_message)
                end
            end
        end

    end

    def vector_sub(vec1, vec2):
        res_vec = [0, 0, 0]
        res_vec[0] = vec1[0] - vec2[0]
        res_vec[1] = vec1[1] - vec2[1]
        res_vec[2] = vec1[2] - vec2[2]
        return res_vec

    end

    def vector_add(vec1, vec2):
        res_vec = [0, 0, 0]
        res_vec[0] = vec1[0] + vec2[0]
        res_vec[1] = vec1[1] + vec2[1]
        res_vec[2] = vec1[2] + vec2[2]
        return res_vec

    end

    def mul_scalar(vec1, s):
        return [vec1[0] * s, vec1[1] * s, vec1[2] * s]

    def solve_2x2(A11, A12, A21, A22, b1, b2, eps=1e-9):
        det = A11 * A22 - A12 * A21
        if abs(det) < eps:
            popup(s="Error", error=True, title="singular or nearly singular 2x2 system", blocking=False)
        inv_det = 1.0 / det
        x = (b1 * A22 - A12 * b2) * inv_det
        y = (-b1 * A21 + A11 * b2) * inv_det
        return x, y

    end

    def circle_from_3points_3d(P1, P2, P3, eps=1e-9):
        v1 = vector_sub(P2, P1)
        v2 = vector_sub(P3, P1)
        n = vector_v_product(v1, v2)
        n_norm = norm(n)
        if n_norm < eps:
            popup(s="Error", error=True, title="three points are collinear or nearly collinear", blocking=False)
        end
        n_hat = normalize(n)
        u = normalize(v1)  # first in-plane axis
        v = vector_v_product(n_hat, u)
        v = normalize(v)

        def to_plane_coords(P):
            d = vector_sub(P, P1)
            return vector_o_product(d, u), vector_o_product(d, v)

        end
        x1, y1 = to_plane_coords(P1)
        x2, y2 = to_plane_coords(P2)
        x3, y3 = to_plane_coords(P3)
        A11 = x2 - x1;
        A12 = y2 - y1
        A21 = x3 - x1;
        A22 = y3 - y1
        b1 = 0.5 * (x2 * x2 + y2 * y2 - x1 * x1 - y1 * y1)
        b2 = 0.5 * (x3 * x3 + y3 * y3 - x1 * x1 - y1 * y1)
        xc, yc = solve_2x2(A11, A12, A21, A22, b1, b2, eps=eps)
        R = norm([x1 - xc, y1 - yc])
        center_3d = vector_add(P1, vector_add(mul_scalar(u, xc), mul_scalar(v, yc)))
        return center_3d, R, n_hat

    end

    def calculate_circle_center_frame_by_start_point(pnt_circle1, pnt_circle2, pnt_circle3):
        center, radius, normal = circle_from_3points_3d(pnt_circle1, pnt_circle2, pnt_circle3)
        vector_x = normalize(vector_sub(pnt_circle1, center))
        vector_z = normal
        vector_y = vector_v_product(vector_z, vector_x)
        center_frame = cal_rpy_frame(center, vector_x, vector_y)
        return center_frame

    end

    def overlay_second_offset(org, first_offset, second_offset, first_offset_normalized):
        proj_length = vector_o_product(second_offset, first_offset_normalized)
        perp_offset_second = [0, 0, 0]
        perp_offset_second[0] = second_offset[0] - proj_length * first_offset_normalized[0]
        perp_offset_second[1] = second_offset[1] - proj_length * first_offset_normalized[1]
        perp_offset_second[2] = second_offset[2] - proj_length * first_offset_normalized[2]
        pnt = elicopy.deepcopy(org)
        pnt[0] = org[0] + first_offset[0] + second_offset[0]
        pnt[1] = org[1] + first_offset[1] + second_offset[1]
        pnt[2] = org[2] + first_offset[2] + second_offset[2]
        return pnt

    end

    def overlay_third_offset(org, first_offset_normalized, second_offset_normalized, first_offset, second_offset,
                             third_offset):
        new_pnt = [0, 0, 0, 0, 0, 0]
        proj_vec = vector_v_product(first_offset_normalized, second_offset_normalized)
        if proj_vec != [0] * len(proj_vec):
            proj_vec = normalize(proj_vec)
        proj_length = vector_o_product(third_offset, proj_vec)
        perp_offset_third = [0, 0, 0]
        perp_offset_third[0] = proj_length * proj_vec[0]
        perp_offset_third[1] = proj_length * proj_vec[1]
        perp_offset_third[2] = proj_length * proj_vec[2]
        offset = perp_offset_third
        new_pnt = [0, 0, 0, 0, 0, 0]
        new_pnt[0] = org[0] + first_offset[0] + second_offset[0] + offset[0]
        new_pnt[1] = org[1] + first_offset[1] + second_offset[1] + offset[1]
        new_pnt[2] = org[2] + first_offset[2] + second_offset[2] + offset[2]
        new_pnt[3] = org[3]
        new_pnt[4] = org[4]
        new_pnt[5] = org[5]
        return new_pnt

    end

    def average_three_points(pnt1, pnt2, pnt3):
        center = [0, 0, 0]
        center[0] = (pnt1[0] + pnt2[0] + pnt3[0]) / 3.0
        center[1] = (pnt1[1] + pnt2[1] + pnt3[1]) / 3.0
        center[2] = (pnt1[2] + pnt2[2] + pnt3[2]) / 3.0
        return center

    end

    def calculate_plane_frame_by_three_points(pnt1, pnt2, pnt3, eps=1e-9):
        base_vec = vector_sub(pnt2, pnt1)
        assist_vec = vector_sub(pnt3, pnt1)
        if norm(base_vec) < eps:
            fail_touch_sense("平面拟合失败，平面点重复。")
        end
        plane_normal = vector_v_product(base_vec, assist_vec)
        if norm(plane_normal) < eps:
            fail_touch_sense("平面拟合失败，平面点近共线。")
        end
        vector_x = normalize(base_vec)
        vector_z = normalize(plane_normal)
        vector_y = vector_v_product(vector_z, vector_x)
        vector_y = normalize(vector_y)
        plane_center = average_three_points(pnt1, pnt2, pnt3)
        plane_frame = cal_rpy_frame(plane_center, vector_x, vector_y)
        return plane_frame

    end

    def project_point_to_plane_local(pnt, plane_frame):
        local_pnt = pose_trans_local(pnt, plane_frame)
        local_pnt[2] = 0
        return local_pnt

    end

    def map_pose_between_frames(pnt, source_frame, target_frame):
        local_pnt = pose_trans_local(pnt, source_frame)
        return pose_trans_world(local_pnt, target_frame)

    end

    def calculate_circle_frame_by_plane_and_circle_points(plane_pnt):
        plane_frame = calculate_plane_frame_by_three_points(plane_pnt[0], plane_pnt[1], plane_pnt[2])
        local_circle1 = project_point_to_plane_local(plane_pnt[3], plane_frame)
        local_circle2 = project_point_to_plane_local(plane_pnt[4], plane_frame)
        local_circle3 = project_point_to_plane_local(plane_pnt[5], plane_frame)
        circle_frame_local = calculate_circle_center_frame_by_start_point(local_circle1, local_circle2, local_circle3)
        return pose_trans_world(circle_frame_local, plane_frame), plane_frame

    end

    def overlay_Offset(org, first_offset, second_offset, first_offset_normalized):
        new_pnt = [0, 0, 0, 0, 0, 0]
        proj_length = vector_o_product(second_offset, first_offset_normalized)
        perp_offset = [0, 0, 0]
        perp_offset[0] = second_offset[0] - proj_length * first_offset_normalized[0]
        perp_offset[1] = second_offset[1] - proj_length * first_offset_normalized[1]
        perp_offset[2] = second_offset[2] - proj_length * first_offset_normalized[2]
        second_offset = perp_offset
        new_pnt[0] = org[0] + first_offset[0] + second_offset[0]
        new_pnt[1] = org[1] + first_offset[1] + second_offset[1]
        new_pnt[2] = org[2] + first_offset[2] + second_offset[2]
        new_pnt[3] = org[3]
        new_pnt[4] = org[4]
        new_pnt[5] = org[5]
        return new_pnt

    end

    def pose_trans_local(world_pnt, frame):
        return pose_trans(pose_inv(frame), world_pnt)

    end

    def pose_trans_world(local_pnt, frame):
        return pose_trans(frame, local_pnt)

    end

    def two_point_get_vec(vec1, vec2):
        result = [0, 0, 0]
        result[0] = vec2[0] - vec1[0]
        result[1] = vec2[1] - vec1[1]
        result[2] = vec2[2] - vec1[2]
        return result

    end

    def vector_o_product(vec1, vec2):
        return vec1[0] * vec2[0] + vec1[1] * vec2[1] + vec1[2] * vec2[2]

    end

    def vector_v_product(vec1, vec2):
        res_vec = [0, 0, 0]
        res_vec[0] = vec1[1] * vec2[2] - vec1[2] * vec2[1]
        res_vec[1] = vec1[2] * vec2[0] - vec1[0] * vec2[2]
        res_vec[2] = vec1[0] * vec2[1] - vec1[1] * vec2[0]
        return res_vec

    end

    def get_vertical_vec_by_three_point(pnt1, pnt2, vec_pnt):
        vec1 = [0, 0, 0]
        vec1[0] = pnt2[0] - pnt1[0]
        vec1[1] = pnt2[1] - pnt1[1]
        vec1[2] = pnt2[2] - pnt1[2]
        vec1 = normalize(vec1)
        vec2 = [0, 0, 0]
        vec2[0] = vec_pnt[0] - ((pnt1[0] + pnt2[0]) / 2.0)
        vec2[1] = vec_pnt[1] - ((pnt1[1] + pnt2[1]) / 2.0)
        vec2[2] = vec_pnt[2] - ((pnt1[2] + pnt2[2]) / 2.0)
        vec2 = normalize(vec2)
        ver_vec = vector_v_product(vec1, vec2)
        return ver_vec

    end

    def two_point_touch_offset(pnt1, pnt2, vec_pnt, touch1, touch2, frame):
        final_change = [0, 0, 0, 0, 0, 0]
        pnt1_local = pose_trans_local(pnt1, frame)
        pnt2_local = pose_trans_local(pnt2, frame)
        vec_pnt_local = pose_trans_local(vec_pnt, frame)
        touch1_local = pose_trans_local(touch1, frame)
        touch2_local = pose_trans_local(touch2, frame)
        ver_vec = get_vertical_vec_by_three_point(pnt1_local, pnt2_local, vec_pnt_local)
        if abs(norm(ver_vec)) < 0.0001:
            return final_change
        end
        if (abs(ver_vec[0]) > abs(ver_vec[1])) & (abs(ver_vec[0]) > abs(ver_vec[2])):
            pnt1_local[0] = 0
            pnt2_local[0] = 0
            touch1_local[0] = 0
            touch2_local[0] = 0
        elif (abs(ver_vec[1]) > abs(ver_vec[0])) & (abs(ver_vec[1]) > abs(ver_vec[2])):
            pnt1_local[1] = 0
            pnt2_local[1] = 0
            touch1_local[1] = 0
            touch2_local[1] = 0
        elif (abs(ver_vec[2]) > abs(ver_vec[0])) & (abs(ver_vec[2]) > abs(ver_vec[1])):
            pnt1_local[2] = 0
            pnt2_local[2] = 0
            touch1_local[2] = 0
            touch2_local[2] = 0
        else:
            return final_change
        end
        final_change[0] = touch1_local[0] - pnt1_local[0]
        final_change[1] = touch1_local[1] - pnt1_local[1]
        final_change[2] = touch1_local[2] - pnt1_local[2]
        new_vec1 = two_point_get_vec(pnt1_local, pnt2_local)
        new_vec1 = normalize(new_vec1)
        if abs(norm(new_vec1)) < 0.0001:
            return final_change
        end
        new_vec2 = two_point_get_vec(touch1_local, touch2_local)
        new_vec2 = normalize(new_vec2)
        if abs(norm(new_vec2)) < 0.0001:
            return final_change
        end
        angle = vector_o_product(new_vec1, new_vec2) / (norm(new_vec1) * norm(new_vec2))
        if angle > 1.0:
            angle = 1.0
        end
        if angle < -1.0:
            angle = -1.0
        end
        angle = acos(angle)
        if abs(angle) < 0.0001:
            return final_change
        end
        judge_vec = vector_v_product(new_vec1, new_vec2)
        if abs(norm(judge_vec)) < 0.0001:
            return final_change
        end
        judge_vec = normalize(judge_vec)
        if abs(judge_vec[0]) > 0.9999:
            if judge_vec[0] > 0:
                final_change[3] = angle
            else:
                final_change[3] = -angle
            end
            return final_change
        end
        if abs(judge_vec[1]) > 0.9999:
            if judge_vec[1] > 0:
                final_change[4] = angle
            else:
                final_change[4] = -angle
            end
            return final_change
        end
        if abs(judge_vec[2]) > 0.9999:
            if judge_vec[2] > 0:
                final_change[5] = angle
            else:
                final_change[5] = -angle
            end
            return final_change
        end
        return final_change

    end

    def last_point_touch_offset(pnt1, pnt2, vec_pnt, touch1, touch2, pnt3, touch3, frame):
        final_change = [0, 0, 0, 0, 0, 0]
        pnt3_local = pose_trans_local(pnt3, frame)
        touch3_local = pose_trans_local(touch3, frame)
        touch1_local = pose_trans_local(touch1, frame)
        touch2_local = pose_trans_local(touch2, frame)
        pnt1_local = pose_trans_local(pnt1, frame)
        pnt2_local = pose_trans_local(pnt2, frame)
        vec_pnt_local = pose_trans_local(vec_pnt, frame)
        ver_vec = get_vertical_vec_by_three_point(pnt1_local, pnt2_local, vec_pnt_local)
        if abs(norm(ver_vec)) < 0.0001:
            return final_change
        end
        if (abs(ver_vec[0]) > abs(ver_vec[1])) & (abs(ver_vec[0]) > abs(ver_vec[2])):
            pnt3_local[0] = 0
            touch3_local[0] = 0
            touch1_local[0] = 0
            touch2_local[0] = 0
        elif (abs(ver_vec[1]) > abs(ver_vec[0])) & (abs(ver_vec[1]) > abs(ver_vec[2])):
            pnt3_local[1] = 0
            touch3_local[1] = 0
            touch1_local[1] = 0
            touch2_local[1] = 0
        elif (abs(ver_vec[2]) > abs(ver_vec[0])) & (abs(ver_vec[2]) > abs(ver_vec[1])):
            pnt3_local[2] = 0
            touch3_local[2] = 0
            touch1_local[2] = 0
            touch2_local[2] = 0
        else:
            return final_change
        end
        new_vec3 = two_point_get_vec(pnt3_local, touch3_local)
        if abs(norm(new_vec3)) < 0.0001:
            return final_change
        end
        new_vec2 = two_point_get_vec(touch1_local, touch2_local)
        if abs(norm(new_vec2)) < 0.0001:
            return final_change
        end
        new_vec2 = normalize(new_vec2)
        value = vector_o_product(new_vec2, new_vec3)
        final_change[0] = new_vec2[0] * value
        final_change[1] = new_vec2[1] * value
        final_change[2] = new_vec2[2] * value
        return final_change

    end

    def cal_new_orientation_point(pnt1, pnt2, pnt3, inner_pnt, vec_pnt):
        new_vec_pnt = elicopy.deepcopy(vec_pnt)
        first_vec = two_point_get_vec(pnt1, pnt2)
        first_vec = normalize(first_vec)
        if abs(norm(first_vec)) < 0.0001:
            return new_vec_pnt
        end
        second_vec = two_point_get_vec(pnt1, pnt3)
        second_vec = normalize(second_vec)
        if abs(norm(second_vec)) < 0.0001:
            return new_vec_pnt
        end
        vec_dir = vector_v_product(first_vec, second_vec)
        vec_dir = normalize(vec_dir)
        if abs(norm(vec_dir)) < 0.0001:
            return new_vec_pnt
        end
        len = vector_o_product(vec_dir, two_point_get_vec(inner_pnt, vec_pnt))
        new_vec_pnt[0] = inner_pnt[0] + vec_dir[0] * len
        new_vec_pnt[1] = inner_pnt[1] + vec_dir[1] * len
        new_vec_pnt[2] = inner_pnt[2] + vec_dir[2] * len
        return new_vec_pnt

    end
    global full_tracking_frame
    full_tracking_frame = [0, 0, 0, 0, 0, 0]
    global full_tracking_thread_id
    full_tracking_thread_id = 0
    global full_reference_current
    full_reference_current = 0

    def full_arc_tracking(maxOffset, type, rcTime=0.1, isConstant=False, current=160.0, lrOffset=0.5, upOffset=0.5,
                          startTime=2.0, startCyc=3, udMax=5, lrMax=5):
        sleep(0.01)

    end

    def full_arc_on(process):
        pass

    end

    def full_arc_off():
        pass

    end

    def full_touch_sense_enable(enable):
        pass

    end

    def full_get_touch_sense_signal():
        return False

    end

    def full_sim_mode_inspection():
        global full_sim_mode
        global full_process
        global in_welding_path
        last_sim_mode = full_sim_mode
        while True:
            if full_sim_mode == last_sim_mode:
                sleep(0.5)
            elif full_sim_mode == False and in_welding_path == True:
                full_arc_on(full_process)
                last_sim_mode = full_sim_mode
            elif in_welding_path == True:
                full_arc_off()
                last_sim_mode = full_sim_mode
        end

    end
    global full_err_id
    full_err_id = 0

    def full_err():
        while True:
            sync()
        end

    end
    full_err_id = start_thread(full_err, ())

    def move_thread_touch_path(org, direction1, direction2, length, speed):
        global move_thread_flag_u23547u20301
        move_thread_flag_u23547u20301 = 1
        movel(touch_sense_offset(org, direction1, direction2, length), 1.3962634, speed)
        move_thread_flag_u23547u20301 = 2

    end
    global full_touch_sense_frame
    full_touch_sense_frame = [0, 0, 0, 0, 0, 0]
    global full_touch_point_new
    full_touch_point_new = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                            [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                            [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global touch_failure_mark
    touch_failure_mark = False
    global touch_failure_frequency
    touch_failure_frequency = 0
    global z_reference_local
    z_reference_local = [0, 0, 0, 0, 0, 0]
    global z_touch_local
    z_touch_local = [0, 0, 0, 0, 0, 0]
    global z_offset_value
    z_offset_value = 0

    def full_searching_touch_sense(org, direction1, direction2, length, speed, tactics):
        global move_thread_flag_u23547u20301
        move_thread_flag_u23547u20301 = 0
        global touch_failure_mark
        global touch_failure_frequency
        full_touch_sense_enable(True)
        move_thread_han_u23547u20301 = start_thread(move_thread_touch_path,
                                                    (org, direction1, direction2, length, speed))
        while (True):
            sleep(0.01)
            if (move_thread_flag_u23547u20301 > 1):
                stop_thread(move_thread_han_u23547u20301)
                full_touch_sense_enable(False)
                popup(s="超过寻位距离", error=True, title="寻位失败", blocking=False)
                if (tactics == 0):
                    touch_failure_frequency = touch_failure_frequency + 1
                    touch_failure_mark = False
                    pause()
                    return [0, 0, 0, 0, 0, 0]
                else:
                    touch_failure_mark = False
                    touch_failure_frequency = 0
                    halt()
            end
            if (full_get_touch_sense_signal()):
                full_touch_sense_enable(False)
                stop_thread(move_thread_han_u23547u20301)
                touch_failure_mark = True
                touch_failure_frequency = 0
                return get_actual_tcp_pose()
            end
            sync()
        end

    end

    def full_cleaner_parameter_setting(cleaner_gun_set_value):
        global cleaner_gun_count_value
        if cleaner_gun_count_value < (cleaner_gun_set_value - 1):
            cleaner_gun_count_value = cleaner_gun_count_value + 1
            return False
        end
        cleaner_gun_count_value = 0
        while True:
            break
            sync()
        end
        while True:
            break
            sync()
        end
        return True

    end

    def full_cylinder_clamping_detect():
        while True:
            return
            sync()
        end

    end

    def full_motor_reset_detect():
        while True:
            return
            sync()
        end

    end

    def full_cut_wire(time):
        unconfigured
        unconfigured
        sleep(time)

    end

    def full_cut_wire_reset():
        unconfigured

    end

    # --------------- End WeldingDevice -----------------
    # --------------- Start FullWeldingFunction -----------------
    class MAGParameterSet:
        def __init__(self,
                     work_type, expert_number, job_number, advance_air_supply, arc_starting_current,
                     arc_starting_voltage, arc_start_time, time_of_transition_c, welding_current, welding_voltage,
                     correction_of_inductance, speed_of_welding, backoff_distance_d, time_of_transition_e,
                     arc_closing_current,
                     arc_closing_voltage, arc_closing_time, welding_wire_reburning, delayed_supply,
                     advance_air_supply_job,
                     arc_start_time_job, speed_of_welding_job, backoff_distance_job, arc_closing_time_job,
                     delayed_supply_job):
            self.work_type = work_type
            self.expert_number = expert_number
            self.job_number = job_number
            self.advance_air_supply = advance_air_supply
            self.arc_starting_current = arc_starting_current
            self.arc_starting_voltage = arc_starting_voltage
            self.arc_start_time = arc_start_time
            self.time_of_transition_c = time_of_transition_c
            self.welding_current = welding_current
            self.welding_voltage = welding_voltage
            self.correction_of_inductance = correction_of_inductance
            self.speed_of_welding = speed_of_welding
            self.backoff_distance_d = backoff_distance_d
            self.time_of_transition_e = time_of_transition_e
            self.arc_closing_current = arc_closing_current
            self.arc_closing_voltage = arc_closing_voltage
            self.arc_closing_time = arc_closing_time
            self.welding_wire_reburning = welding_wire_reburning
            self.delayed_supply = delayed_supply
            self.advance_air_supply_job = advance_air_supply_job
            self.arc_start_time_job = arc_start_time_job
            self.speed_of_welding_job = speed_of_welding_job
            self.backoff_distance_job = backoff_distance_job
            self.arc_closing_time_job = arc_closing_time_job
            self.delayed_supply_job = delayed_supply_job

    MAGParameters = [
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
        MAGParameterSet(4, 1, 1, 0.5, 105, 0.0, 0.5, 0.5, 0.0, 0.0, 0, 6.0, 5, 0.5, 60, 0.0, 0.5, 0, 0.5, 0.5, 0.5, 6,
                        5, 1.5, 0.5),
    ]
    # --------------- END FullWeldingFunction -----------------
    # --------------- Full touch sense -----------------
    global full_touch_process_index
    full_touch_process_index = -1
    global full_touch_point_1
    full_touch_point_1 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                          [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                          [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_point_2
    full_touch_point_2 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                          [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                          [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_point_3
    full_touch_point_3 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                          [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                          [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_point_4
    full_touch_point_4 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                          [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                          [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_point_5
    full_touch_point_5 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                          [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                          [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_point_6
    full_touch_point_6 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                          [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                          [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_point_7
    full_touch_point_7 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                          [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                          [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_point_8
    full_touch_point_8 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                          [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                          [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_point_9
    full_touch_point_9 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                          [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                          [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_point_10
    full_touch_point_10 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_point_11
    full_touch_point_11 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_point_12
    full_touch_point_12 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_point_13
    full_touch_point_13 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_point_14
    full_touch_point_14 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_point_15
    full_touch_point_15 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_point_16
    full_touch_point_16 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_point_17
    full_touch_point_17 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_point_18
    full_touch_point_18 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_point_19
    full_touch_point_19 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_point_20
    full_touch_point_20 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_point_21
    full_touch_point_21 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_point_22
    full_touch_point_22 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_point_23
    full_touch_point_23 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_point_24
    full_touch_point_24 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_point_25
    full_touch_point_25 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_point_26
    full_touch_point_26 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_point_27
    full_touch_point_27 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_point_28
    full_touch_point_28 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_point_29
    full_touch_point_29 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_point_30
    full_touch_point_30 = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_sense_offset_value_array
    full_touch_sense_offset_value_array = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                                           [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    global full_touch_reference_point_array
    full_touch_reference_point_array = [[0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                                        [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                                        [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                                        [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                                        [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                                        [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                                        [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                                        [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                                        [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                                        [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                                        [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                                        [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                                        [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                                        [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
                                        [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]]
    # --------------- END Full touch sense -----------------
    # Set Node Preamble Script.
    # Loop Node Preamble Script.
    # Waypoint Node Preamble Script.
    # Initial Variables value.
    # Timer Node Preamble Script.
    # SubTask Node Preamble Script.
    # Before Start Preamble Script.
    # Thread Node Preamble Script.
    # Event Node Preamble Script.
    # Main Task Script.
    while (True):
        $ LINE: (2, "机器人主任务")
        # Begin: ELITECO Plugin Task Node
        #   Source: FullFunctionWelding, 1.3.0, ELITE ROBOTS
        #   Type: 快速编程工具
        $ LINE: (3, "快速编程工具")
        full_repeatable(0)
        # Begin: ELITECO Plugin Task Node
        #   Source: FullFunctionWelding, 1.3.0, ELITE ROBOTS
        #   Type: 直线焊接程序_1
        $ LINE: (4, "直线焊接程序_1")
        # Begin: ELITECO Plugin Task Node
        #   Source: FullFunctionWelding, 1.3.0, ELITE ROBOTS
        #   Type: 开始点_1
        $ LINE: (5, "开始点_1")
        movej(get_inverse_kin(full_apply_touch_offset(
            [0.5440111730677202, -0.14749999994974966, 0.6247965102173798, -3.1342916479706173, 1.7687132141204421E-10,
             -1.5707963266910507]), qnear=[0.0, -1.570796327, -1.0398965451672155, -1.570796327, 1.570796327, 0.0]),
              1.396263401670578, 1.0471975511965976, 0, 0.0)
        # End: ELITECO Plugin Task Node
        # Begin: ELITECO Plugin Task Node
        #   Source: FullFunctionWelding, 1.3.0, ELITE ROBOTS
        #   Type: MAG焊接开始ku No.1
        $ LINE: (6, "MAG焊接开始ku No.1")
        in_welding_path = True
        full_arc_on(0)
        # End: ELITECO Plugin Task Node
        # Begin: ELITECO Plugin Task Node
        #   Source: FullFunctionWelding, 1.3.0, ELITE ROBOTS
        #   Type: 摆动开始 No.1
        $ LINE: (7, "摆动开始 No.1")
